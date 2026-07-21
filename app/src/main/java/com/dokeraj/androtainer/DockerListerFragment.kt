package com.dokeraj.androtainer

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.util.Linkify
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.PopupMenu
import androidx.activity.addCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.dokeraj.androtainer.adapter.DockerContainerAdapter
import com.dokeraj.androtainer.adapter.DockerEndpointAdapter
import com.dokeraj.androtainer.databinding.FragmentDockerListerBinding
import com.dokeraj.androtainer.dialogs.ShowHiddenFeaturesDiag
import com.dokeraj.androtainer.globalvars.GlobalApp
import com.dokeraj.androtainer.models.ContainerStateType
import com.dokeraj.androtainer.models.HealthState
import com.dokeraj.androtainer.models.Kontainer
import com.dokeraj.androtainer.models.KontainerSortField
import com.dokeraj.androtainer.models.KontainerStateFilter
import com.dokeraj.androtainer.models.KontainerStats
import com.dokeraj.androtainer.util.DataState
import com.dokeraj.androtainer.util.KontainerListOrganizer
import com.dokeraj.androtainer.viewmodels.DockerListerViewModel
import com.dokeraj.androtainer.viewmodels.MainStateEvent
import dagger.hilt.android.AndroidEntryPoint
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.linkify.LinkifyPlugin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.Locale

@AndroidEntryPoint
class DockerListerFragment : Fragment(R.layout.fragment_docker_lister) {
    private var _binding: FragmentDockerListerBinding? = null
    private val binding get() = _binding!!
    private val args: DockerListerFragmentArgs by navArgs()
    private var lastTimePressed: Long = 0L
    private val intervalToastTime = 1200

    private var allContainers: List<Kontainer> = emptyList()
    private var latestStatsById: Map<String, KontainerStats> = emptyMap()
    private var currentSearchTerm: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDockerListerBinding.bind(view)

        /** how to instantiate a viewModel object*/
        val model: DockerListerViewModel =
            ViewModelProvider(requireActivity()).get(DockerListerViewModel::class.java)

        @Suppress("DEPRECATION")
        requireActivity().window.statusBarColor =
            ContextCompat.getColor(requireContext(), R.color.dis2)

        val globActivity: MainActiviy = (activity as MainActiviy?)!!
        val globalVars: GlobalApp = (globActivity.application as GlobalApp)

        binding.tvContainerListerEndpointName.text = globalVars.currentUser!!.currentEndpoint.name

        setDrawerInfo(globalVars)

        val hamburgerMenu = ActionBarDrawerToggle(activity,
            binding.drawerLister,
            binding.toolbarMenu,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close)

        hamburgerMenu.drawerArrowDrawable.color =
            ContextCompat.getColor(requireContext(), R.color.disText2)
        binding.drawerLister.addDrawerListener(hamburgerMenu)
        hamburgerMenu.syncState()

        if (globActivity.getIsLoginToDockerLister()) {
            globActivity.setIsLoginToDockerLister(false)
            model.setStateEvent(MainStateEvent.InitializeView(args.dContainers.containers))
        }

        val recyclerAdapter =
            DockerContainerAdapter(
                globalVars.currentUser!!.serverUrl,
                globalVars.currentUser!!.jwt!!,
                globalVars.currentUser!!.isUsingApiKey,
                globalVars.currentUser!!.currentEndpoint.id,
                globalVars,
                requireContext(), this, model)
        binding.recyclerView.adapter = recyclerAdapter
        binding.recyclerView.layoutManager = LinearLayoutManager(activity)
        binding.recyclerView.setHasFixedSize(true)

        binding.rvDockerEndpoints.adapter = DockerEndpointAdapter(globalVars.currentUser!!.listOfEndpoints,
            globalVars, requireContext(), globActivity, binding.drawerLister, model, recyclerAdapter, this)
        binding.rvDockerEndpoints.layoutManager = LinearLayoutManager(activity)
        binding.rvDockerEndpoints.setHasFixedSize(true)

        binding.btnLogout.setOnClickListener {
            logout(globActivity)
        }

        binding.btnAbout.setOnClickListener {
            if (binding.tvAboutInfo.isVisible) {
                binding.tvAboutInfo.visibility = View.INVISIBLE
                binding.btnHiddenFeatures.visibility = View.INVISIBLE
            } else {
                binding.tvAboutInfo.visibility = View.VISIBLE
                binding.btnHiddenFeatures.visibility = View.VISIBLE
            }
        }

        // initialize the filter chips + sort button from persisted settings
        initFilterChips(globActivity, globalVars, recyclerAdapter)
        initSortButton(globActivity, globalVars, recyclerAdapter)

        binding.btnHiddenFeatures.setOnClickListener {
            val dialog = ShowHiddenFeaturesDiag()
            dialog.show(parentFragmentManager, "Hidden Explanations")
        }

        binding.btnEndpoints.setOnClickListener {
            if (binding.rvDockerEndpoints.isVisible)
                binding.rvDockerEndpoints.visibility = View.GONE
            else {
                binding.rvDockerEndpoints.visibility = View.VISIBLE
            }
        }

        binding.btnManageUsers.setOnClickListener {
            val action =
                DockerListerFragmentDirections.actionDockerListerFragmentToUsersListerFragment()
            findNavController().navigate(action)
        }

        binding.swiperLayout.setOnRefreshListener {
            callSwiperLogic(model, globActivity, globalVars, recyclerAdapter)
        }

        binding.etSearchTerm.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s.toString() == "")
                    filterContainersByTerm(recyclerAdapter, null)
                else
                    filterContainersByTerm(recyclerAdapter, s.toString().lowercase(Locale.getDefault()))
            }
        })

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, true) {
            // hijack the back button press and don't allow going back to login page (only close the drawer)
            if (binding.drawerLister.isDrawerOpen(GravityCompat.START))
                binding.drawerLister.closeDrawer(GravityCompat.START)
            else {
                if (lastTimePressed < System.currentTimeMillis() - intervalToastTime) {
                    globActivity.showGenericSnack(requireContext(),
                        requireView(),
                        "Press back again to close the app",
                        R.color.blue_main,
                        R.color.dis2, intervalToastTime)
                    lastTimePressed = System.currentTimeMillis()
                } else
                    globActivity.moveTaskToBack(true)
            }
        }

        subscribeObservers(model, recyclerAdapter, globActivity)
    }

    private fun subscribeObservers(
        dataViewModel: DockerListerViewModel,
        recyclerAdapter: DockerContainerAdapter,
        mainActivity: MainActiviy,
    ) {
        dataViewModel.dataState.observe(viewLifecycleOwner, { ds ->
            when (ds) {
                is DataState.Success<List<Kontainer>> -> {
                    allContainers = ds.data
                    recyclerAdapter.submitList(getFilteredList())
                    binding.swiperLayout.isRefreshing = false
                    setContainerStats(ds.data)
                    callGetLiveStats(dataViewModel)
                }
                is DataState.Error -> {
                    binding.swiperLayout.isRefreshing = false
                    logout(mainActivity, "Issue with Portainer! Please login again.")
                }
                is DataState.Loading -> {
                    binding.swiperLayout.isRefreshing = true
                    setContainerStats(listOf(), true)
                }
                /** below these is the logic for handling the idividual cards*/
                is DataState.CardLoading -> {
                    allContainers = ds.data
                    recyclerAdapter.submitList(getFilteredList())
                    setContainerStats(listOf(), true)
                }
                is DataState.CardSuccess -> {
                    allContainers = ds.data
                    binding.swiperLayout.isRefreshing = false
                    recyclerAdapter.submitList(getFilteredList())
                    setContainerStats(ds.data)
                }
                is DataState.CardError -> {
                    allContainers = ds.data
                    recyclerAdapter.submitList(getFilteredList())
                    setContainerStats(ds.data)
                }

                else -> {}
            }
        })

        // live CPU/MEM per card — separate stream, so no interplay with dataState
        dataViewModel.statsState.observe(viewLifecycleOwner, { stats ->
            latestStatsById = stats ?: emptyMap()
            recyclerAdapter.updateStats(latestStatsById)
            val sortField = ((activity as MainActiviy).application as GlobalApp)
                .appSettings?.sortField
            if (sortField == KontainerSortField.CPU || sortField == KontainerSortField.MEMORY) {
                recyclerAdapter.submitList(getFilteredList())
            }
        })
    }

    /** batch-fetch one stats sample per running container after each list load */
    private fun callGetLiveStats(dataViewModel: DockerListerViewModel) {
        val globalVars: GlobalApp = ((activity as MainActiviy?)!!.application as GlobalApp)
        val user = globalVars.currentUser ?: return

        val urlTemplate = getString(R.string.getContainerStats)
            .replace("{baseUrl}", user.serverUrl.removeSuffix("/"))
            .replace("{endpointId}", user.currentEndpoint.id.toString())

        dataViewModel.setStateEvent(MainStateEvent.GetStats(jwt = user.jwt,
            urlTemplate = urlTemplate,
            isUsingApiKey = user.isUsingApiKey))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun callGetContainers(
        dataViewModel: DockerListerViewModel,
        url: String,
        jwt: String,
        isUsingApiKey: Boolean,
        endpointId: Int,
    ) {
        val fullUrl =
            getString(R.string.getDockerContainers).replace("{baseUrl}", url.removeSuffix("/"))
                .replace("{endpointId}", endpointId.toString())

        dataViewModel.setStateEvent(MainStateEvent.GetKontejneri(jwt = jwt,
            url = fullUrl,
            isUsingApiKey))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun callSwiperLogic(
        dataViewModel: DockerListerViewModel,
        globActivity: MainActiviy,
        globalVars: GlobalApp,
        recyclerAdapter: DockerContainerAdapter,
    ) {
        if (globActivity.isJwtValid()) {
            // don't refresh if there are any items that are transitioning between states
            val hasTransitioning = allContainers.any { it.state == ContainerStateType.TRANSITIONING }

            if (hasTransitioning)
                binding.swiperLayout.isRefreshing = false
            else {
                callGetContainers(dataViewModel,
                    globalVars.currentUser!!.serverUrl,
                    globalVars.currentUser!!.jwt!!,
                    globalVars.currentUser!!.isUsingApiKey,
                    globalVars.currentUser!!.currentEndpoint.id)
            }
        } else {
            logout(globActivity, logoutMsg = "Session has expired! Please log in again.")
        }
    }

    private fun setDrawerInfo(globalVars: GlobalApp) {
        // set the name of the logged in user and the server url
        binding.listerHeder.tvLoggedUsername.text = globalVars.currentUser!!.username
        binding.listerHeder.tvLoggedUrl.text = globalVars.currentUser!!.serverUrl

        //get version name of app
        val pInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
        val appVersion: String? = pInfo.versionName

        // use Markwon to format the text
        val markwon = Markwon.builder(requireContext())
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder
                        .codeTextColor(ContextCompat.getColor(requireContext(), R.color.blue_main))
                        .linkColor(ContextCompat.getColor(requireContext(), R.color.teal_200))
                }
            }).usePlugin(LinkifyPlugin.create(Linkify.EMAIL_ADDRESSES or Linkify.WEB_URLS))
            .build()

        // get the text from the string resources and add the version number
        markwon.setMarkdown(binding.tvAboutInfo, getString(R.string.about_app, appVersion))
    }

    private fun logout(mainActivity: MainActiviy, logoutMsg: String? = null) {
        mainActivity.invalidateJwt()
        mainActivity.setLogoutMsg(logoutMsg)

        val action =
            DockerListerFragmentDirections.actionDockerListerFragmentToHomeFragment()
        findNavController().navigate(action)
    }

    private fun setContainerStats(allContainers: List<Kontainer>, isLoading: Boolean = false) {
        if (isLoading) {
            listOf(
                binding.tvSummaryRunning,
                binding.tvSummaryUnhealthy,
                binding.tvSummaryExited,
                binding.tvSummaryTotal,
            ).forEach { it.text = "—" }
            binding.tvContainerCount.setText(R.string.containers_loading)
            return
        }

        val running = allContainers.count { it.state == ContainerStateType.RUNNING }
        val unhealthy = allContainers.count { it.health == HealthState.UNHEALTHY }
        val exited = allContainers.count {
            it.state == ContainerStateType.EXITED || it.state == ContainerStateType.ERRORED
        }
        val healthy = allContainers.count { it.health == HealthState.HEALTHY }
        val created = allContainers.count { it.state == ContainerStateType.CREATED }

        binding.tvSummaryRunning.text = running.toString()
        binding.tvSummaryUnhealthy.text = unhealthy.toString()
        binding.tvSummaryExited.text = exited.toString()
        binding.tvSummaryTotal.text = allContainers.size.toString()
        binding.tvContainerCount.text = resources.getQuantityString(
            R.plurals.containers_count,
            allContainers.size,
            allContainers.size,
        )
        binding.chipFilterRunning.text = getString(R.string.filter_running) + " $running"
        binding.chipFilterHealthy.text = getString(R.string.filter_healthy) + " $healthy"
        binding.chipFilterExited.text = getString(R.string.filter_exited) + " $exited"
        binding.chipFilterCreated.text = getString(R.string.filter_created) + " $created"
    }

    private fun filterContainersByTerm(
        recyclerAdapter: DockerContainerAdapter,
        searchTerm: String?,
    ) {
        currentSearchTerm = searchTerm
        recyclerAdapter.submitList(getFilteredList())
    }

    /** seed the multi-select filter chips from persisted settings and wire changes back */
    private fun initFilterChips(
        globActivity: MainActiviy,
        globalVars: GlobalApp,
        recyclerAdapter: DockerContainerAdapter,
    ) {
        val chipByFilter = mapOf(
            KontainerStateFilter.RUNNING to binding.chipFilterRunning,
            KontainerStateFilter.HEALTHY to binding.chipFilterHealthy,
            KontainerStateFilter.EXITED to binding.chipFilterExited,
            KontainerStateFilter.CREATED to binding.chipFilterCreated)

        val persisted = globalVars.appSettings!!.stateFilters ?: emptyList()
        chipByFilter.forEach { (filter, chip) -> chip.isChecked = filter in persisted }

        binding.cgStateFilters.setOnCheckedStateChangeListener { _, _ ->
            val selected: List<KontainerStateFilter> =
                chipByFilter.filter { (_, chip) -> chip.isChecked }.keys.toList()
            globActivity.setGlobalAppSettings(stateFilters = selected)
            recyclerAdapter.submitList(getFilteredList())
        }
    }

    /** sort menu: re-selecting the active field toggles direction */
    private fun initSortButton(
        globActivity: MainActiviy,
        globalVars: GlobalApp,
        recyclerAdapter: DockerContainerAdapter,
    ) {
        updateSortButtonLabel(globalVars)
        binding.btnSort.setOnClickListener { anchor ->
            val popupContext = ContextThemeWrapper(
                requireContext(),
                R.style.ThemeOverlay_AndroTainer_PopupMenu,
            )
            val popup = PopupMenu(popupContext, anchor)
            popup.menuInflater.inflate(R.menu.menu_sort, popup.menu)

            val currentField = globalVars.appSettings!!.sortField ?: KontainerSortField.NAME
            val currentAsc = globalVars.appSettings!!.sortAscending ?: true
            val arrow = if (currentAsc) "▲" else "▼"
            val itemByField = mapOf(
                KontainerSortField.NAME to popup.menu.findItem(R.id.sortByName),
                KontainerSortField.UPTIME to popup.menu.findItem(R.id.sortByUptime),
                KontainerSortField.CREATED to popup.menu.findItem(R.id.sortByCreated),
                KontainerSortField.CPU to popup.menu.findItem(R.id.sortByCpu),
                KontainerSortField.MEMORY to popup.menu.findItem(R.id.sortByMemory),
            )
            itemByField[currentField]?.let { it.title = "${it.title} $arrow" }

            popup.setOnMenuItemClickListener { item ->
                val pickedField = when (item.itemId) {
                    R.id.sortByName -> KontainerSortField.NAME
                    R.id.sortByUptime -> KontainerSortField.UPTIME
                    R.id.sortByCreated -> KontainerSortField.CREATED
                    R.id.sortByCpu -> KontainerSortField.CPU
                    R.id.sortByMemory -> KontainerSortField.MEMORY
                    else -> return@setOnMenuItemClickListener false
                }
                val newAscending = if (pickedField == currentField) {
                    !currentAsc // re-tap toggles direction
                } else {
                    pickedField == KontainerSortField.NAME // NAME starts asc, others desc
                }
                globActivity.setGlobalAppSettings(sortField = pickedField,
                    sortAscending = newAscending)
                updateSortButtonLabel(globalVars, pickedField, newAscending)
                recyclerAdapter.submitList(getFilteredList()) {
                    binding.recyclerView.scrollToPosition(0)
                }
                true
            }
            popup.show()
        }
    }

    private fun updateSortButtonLabel(
        globalVars: GlobalApp,
        field: KontainerSortField = globalVars.appSettings!!.sortField ?: KontainerSortField.NAME,
        ascending: Boolean = globalVars.appSettings!!.sortAscending ?: true,
    ) {
        val fieldLabel = getString(when (field) {
            KontainerSortField.NAME -> R.string.sort_by_name
            KontainerSortField.UPTIME -> R.string.sort_by_uptime
            KontainerSortField.CREATED -> R.string.sort_by_created
            KontainerSortField.CPU -> R.string.sort_by_cpu
            KontainerSortField.MEMORY -> R.string.sort_by_memory
        })
        binding.btnSort.text = getString(
            R.string.sort_button_label,
            fieldLabel,
            if (ascending) "↑" else "↓",
        )
    }

    private fun getFilteredList(): List<Kontainer> {
        val globActivity: MainActiviy = (activity as MainActiviy?)!!
        val globalVars: GlobalApp = (globActivity.application as GlobalApp)
        val settings = globalVars.appSettings!!

        val filtered = KontainerListOrganizer.filter(
            allContainers,
            (settings.stateFilters ?: emptyList()).toSet(),
            currentSearchTerm)

        return KontainerListOrganizer.sort(
            filtered,
            settings.sortField ?: KontainerSortField.NAME,
            settings.sortAscending ?: true,
            latestStatsById,
        )
    }
}

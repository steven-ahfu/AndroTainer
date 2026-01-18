package com.dokeraj.androtainer

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.util.Linkify
import android.view.View
import androidx.activity.addCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.dokeraj.androtainer.adapter.DockerContainerAdapter
import com.dokeraj.androtainer.adapter.DockerEndpointAdapter
import com.dokeraj.androtainer.databinding.FragmentDockerContainerDetailsBinding
import com.dokeraj.androtainer.databinding.FragmentDockerListerBinding
import com.dokeraj.androtainer.dialogs.ShowHiddenFeaturesDiag
import com.dokeraj.androtainer.globalvars.GlobalApp
import com.dokeraj.androtainer.models.ContainerStateType
import com.dokeraj.androtainer.models.Kontainer
import com.dokeraj.androtainer.models.KontainerFilterPref
import com.dokeraj.androtainer.util.DataState
import com.dokeraj.androtainer.viewmodels.DockerListerViewModel
import com.dokeraj.androtainer.viewmodels.MainStateEvent
import dagger.hilt.android.AndroidEntryPoint
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.linkify.LinkifyPlugin
import kotlinx.coroutines.ExperimentalCoroutinesApi

@AndroidEntryPoint
class DockerListerFragment : Fragment(R.layout.fragment_docker_lister) {
    private var _binding: FragmentDockerListerBinding? = null
    private val binding get() = _binding!!
    private val args: DockerListerFragmentArgs by navArgs()
    private var lastTimePressed: Long = 0L
    private val intervalToastTime = 1200

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDockerListerBinding.bind(view)

        /** how to instantiate a viewModel object*/
        val model: DockerListerViewModel =
            ViewModelProvider(requireActivity()).get(DockerListerViewModel::class.java)

        requireActivity().window.statusBarColor =
            ContextCompat.getColor(requireContext(), R.color.dis2)

        val globActivity: MainActiviy = (activity as MainActiviy?)!!
        val globalVars: GlobalApp = (globActivity.application as GlobalApp)

        binding.tvContainerListerEndpointName.text = globalVars.currentUser!!.currentEndpoint.name

        setDrawerInfo(globalVars)

        val hamburgerMenu = ActionBarDrawerToggle(activity,
            binding.drawerLister,
            binding.toolbarMenu,
            R.string.nav_app_bar_open_drawer_description,
            R.string.navigation_drawer_close)

        hamburgerMenu.drawerArrowDrawable.color =
            ContextCompat.getColor(requireContext(), R.color.disText2)
        binding.drawerLister.addDrawerListener(hamburgerMenu)
        hamburgerMenu.syncState()

        if (globActivity.getIsLoginToDockerLister()) {
            globActivity.setIsLoginToDockerLister(false)
            model.setStateEvent(MainStateEvent.InitializeView(args.dContainers.containers))
        }

        // just give an empty list of containers when initializing the recyclerAdapter
        // we will fill the adapter when the modelview is initialized
        val containers: List<Kontainer> = listOf()

        val recyclerAdapter =
            DockerContainerAdapter(containers,
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
            if (binding.tvAboutInfo.visibility == View.VISIBLE) {
                binding.tvAboutInfo.visibility = View.INVISIBLE
                binding.btnHiddenFeatures.visibility = View.INVISIBLE
            } else {
                binding.tvAboutInfo.visibility = View.VISIBLE
                binding.btnHiddenFeatures.visibility = View.VISIBLE
            }
        }

        // initialize the filtering
        when (globalVars.appSettings!!.kontainerFilter) {
            KontainerFilterPref.RUNNING -> filterContainers(recyclerAdapter,
                globActivity,
                binding.clStatsRunning,
                listOf(binding.clStatsTotal, binding.clStatsStopped),
                KontainerFilterPref.RUNNING)
            KontainerFilterPref.STOPPED_OR_ERRORED -> filterContainers(recyclerAdapter,
                globActivity,
                binding.clStatsStopped,
                listOf(binding.clStatsTotal, binding.clStatsRunning),
                KontainerFilterPref.STOPPED_OR_ERRORED)
            KontainerFilterPref.TOTAL -> filterContainers(recyclerAdapter,
                globActivity,
                binding.clStatsTotal,
                listOf(binding.clStatsRunning, binding.clStatsStopped),
                KontainerFilterPref.TOTAL)
        }

        // initialize the searchTermVisibility
        if (globalVars.appSettings!!.searchTermVisibility)
            binding.llSearchTerm.visibility = View.VISIBLE
        else
            binding.llSearchTerm.visibility = View.GONE


        // container filtering
        binding.clStatsRunning.setOnClickListener {
            filterContainers(recyclerAdapter,
                globActivity,
                binding.clStatsRunning,
                listOf(binding.clStatsTotal, binding.clStatsStopped),
                KontainerFilterPref.RUNNING)
        }
        binding.clStatsStopped.setOnClickListener {
            filterContainers(recyclerAdapter,
                globActivity,
                binding.clStatsStopped,
                listOf(binding.clStatsTotal, binding.clStatsRunning),
                KontainerFilterPref.STOPPED_OR_ERRORED)
        }
        binding.clStatsTotal.setOnClickListener {
            filterContainers(recyclerAdapter,
                globActivity,
                binding.clStatsTotal,
                listOf(binding.clStatsRunning, binding.clStatsStopped),
                KontainerFilterPref.TOTAL)
        }

        binding.btnHiddenFeatures.setOnClickListener {
            val dialog = ShowHiddenFeaturesDiag()
            dialog.show(parentFragmentManager, "Hidden Explanations")
        }

        binding.btnEndpoints.setOnClickListener {
            if (binding.rvDockerEndpoints.visibility == View.VISIBLE)
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

        binding.clStatsTotal.setOnLongClickListener {
            if (binding.llSearchTerm.visibility == View.VISIBLE) {
                binding.etSearchTerm.setText("")
                binding.llSearchTerm.visibility = View.GONE
                globActivity.setGlobalAppSettings(searchTermVisibility = false)
            } else {
                binding.llSearchTerm.visibility = View.VISIBLE
                globActivity.setGlobalAppSettings(searchTermVisibility = true)
            }

            true
        }

        binding.etSearchTerm.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s.toString() == "")
                    filterContainersByTerm(recyclerAdapter, null)
                else
                    filterContainersByTerm(recyclerAdapter, s.toString().toLowerCase())
            }
        })

        binding.clDeleteTerm.setOnClickListener {
            binding.etSearchTerm.setText("")
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, true) {
            // hijack the back button press and don't allow going back to login page (only close the drawer)
            if (binding.drawerLister.isDrawerOpen(GravityCompat.START))
                binding.drawerLister.close()
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
                    recyclerAdapter.setItems(ds.data)
                    recyclerAdapter.notifyDataSetChanged()
                    binding.swiperLayout.isRefreshing = false
                    setContainerStats(ds.data)
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
                    recyclerAdapter.setItems(ds.data)
                    recyclerAdapter.notifyDataSetChanged()
                    //recyclerAdapter.notifyItemChanged(ds.itemIndex)
                    setContainerStats(listOf(), true)
                }
                is DataState.CardSuccess -> {
                    binding.swiperLayout.isRefreshing = false
                    recyclerAdapter.setItems(ds.data)
                    recyclerAdapter.notifyDataSetChanged()
                    //recyclerAdapter.notifyItemChanged(ds.itemIndex)
                    setContainerStats(ds.data)
                }
                is DataState.CardError -> {
                    recyclerAdapter.setItems(ds.data)
                    recyclerAdapter.notifyDataSetChanged()
                    //recyclerAdapter.notifyItemChanged(ds.itemIndex)
                    setContainerStats(ds.data)
                }
            }
        })
    }

    @ExperimentalCoroutinesApi
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

    @ExperimentalCoroutinesApi
    fun callSwiperLogic(
        dataViewModel: DockerListerViewModel,
        globActivity: MainActiviy,
        globalVars: GlobalApp,
        recyclerAdapter: DockerContainerAdapter,
    ) {
        if (globActivity.isJwtValid()) {
            // don't refresh if there are any items that are transitioning between states
            if (recyclerAdapter.areItemsInTransitioningState())
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
        val appVersion: String = pInfo.versionName

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
        fun showProgressBar(show: Boolean) {
            val pbAndTextVisibility = if (show)
                Pair(View.VISIBLE, View.INVISIBLE)
            else
                Pair(View.INVISIBLE, View.VISIBLE)

            binding.pbTotalStats.visibility = pbAndTextVisibility.first
            binding.pbRunningStats.visibility = pbAndTextVisibility.first
            binding.pbStoppedStats.visibility = pbAndTextVisibility.first
            binding.tvTotalStat.visibility = pbAndTextVisibility.second
            binding.tvStoppedStat.visibility = pbAndTextVisibility.second
            binding.tvRunningStat.visibility = pbAndTextVisibility.second
        }
        if (!isLoading) {
            binding.tvTotalStat.text = allContainers.size.toString()
            binding.tvRunningStat.text = allContainers.count { kon ->
                kon.state == ContainerStateType.RUNNING
            }.toString()
            binding.tvStoppedStat.text = allContainers.count { kon ->
                kon.state == ContainerStateType.EXITED || kon.state == ContainerStateType.ERRORED
            }.toString()

            // if there are any more containers that are in transitioning state, don't show the stats, instead keep the spinner
            if (allContainers.any { it.state == ContainerStateType.TRANSITIONING })
                showProgressBar(true)
            else
                showProgressBar(false)
        } else {
            showProgressBar(true)
        }
    }

    private fun filterContainersByTerm(
        recyclerAdapter: DockerContainerAdapter,
        searchTerm: String?,
    ) {
        recyclerAdapter.containerSearchTerm = searchTerm
        recyclerAdapter.notifyDataSetChanged()
    }

    private fun filterContainers(
        recyclerAdapter: DockerContainerAdapter,
        globActivity: MainActiviy,
        layoutToSelect: ConstraintLayout,
        layoutsToDeselect: List<ConstraintLayout>,
        filterPreference: KontainerFilterPref,
    ) {
        layoutToSelect.background = ContextCompat.getDrawable(requireContext(),
            R.drawable.square_stats_shape_item_selected)

        layoutsToDeselect.forEach {
            it.background = ContextCompat.getDrawable(requireContext(),
                R.drawable.square_stats_shape_item)
        }
        globActivity.setGlobalAppSettings(filterPreference)
        recyclerAdapter.notifyDataSetChanged()
    }
}
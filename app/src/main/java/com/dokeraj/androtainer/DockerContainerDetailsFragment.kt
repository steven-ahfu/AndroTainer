package com.dokeraj.androtainer

import android.os.Bundle
import android.text.util.Linkify
import android.view.View
import androidx.activity.addCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.dokeraj.androtainer.buttons.BtnDeleteContainer
import com.dokeraj.androtainer.databinding.FragmentDockerContainerDetailsBinding
import com.dokeraj.androtainer.globalvars.GlobalApp
import com.dokeraj.androtainer.models.Kontainer
import com.dokeraj.androtainer.models.logos.Logo
import com.dokeraj.androtainer.models.logos.Logos
import com.dokeraj.androtainer.util.DataState
import com.dokeraj.androtainer.viewmodels.DockerListerViewModel
import com.dokeraj.androtainer.viewmodels.MainStateEvent
import com.google.gson.Gson
import com.squareup.picasso.Picasso
import dagger.hilt.android.AndroidEntryPoint
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.linkify.LinkifyPlugin
import java.time.Instant.ofEpochSecond
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.Locale.getDefault

@AndroidEntryPoint
class DockerContainerDetailsFragment : Fragment(R.layout.fragment_docker_container_details) {
    private var _binding: FragmentDockerContainerDetailsBinding? = null
    private val binding get() = _binding!!
    var isDeletingNow: Boolean = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentDockerContainerDetailsBinding.bind(view)

        val args: DockerContainerDetailsFragmentArgs by navArgs()

        /** how to instantiate a viewModel object*/
        val model = ViewModelProvider(requireActivity()).get(DockerListerViewModel::class.java)

        val selectedContainer: Kontainer = args.dContainer

        binding.tbContainerDetails.navigationIcon =
            ContextCompat.getDrawable(requireActivity(), R.drawable.ic_back)

        val btnDeleteState =
            BtnDeleteContainer(requireContext(), binding.btnContainerDelete)

        val globActivity: MainActiviy = (activity as MainActiviy?)!!
        val globalVars: GlobalApp = (globActivity.application as GlobalApp)

        binding.tvContainerDetailsTitle.text = selectedContainer.name
        binding.tvContainerDetailsEndpointName.text = globalVars.currentUser!!.currentEndpoint.name

        setContainerDetails(selectedContainer)

        val allLogos: Logos =
            Gson().fromJson<Logos>(getString(R.string.allServicesLogos), Logos::class.java)

        val logoToDisplay: Logo? = allLogos.find { logo ->
            logo.names.any { lName ->
                lName.lowercase(getDefault()) in selectedContainer.pulledImage.lowercase(getDefault())
            }
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, true) {
            // Only allow to go back to docker lister only when we get any kind of response from the API (or timeout)
            if (!isDeletingNow) {
                model.setStateEvent(MainStateEvent.SetSuccess)
            } else
                globActivity.showGenericSnack(requireContext(),
                    requireView(),
                    "Please wait until the ${selectedContainer.name} is deleted",
                    R.color.dis4,
                    R.color.blue_main)
        }

        logoToDisplay?.let {
            Picasso.get().load(it.url)
                .resize(it.width, it.height)
                .into(binding.ivContainerLogo)
        }

        // on nav button back clicked set the global var that the swiperRefresh should be turned on
        binding.tbContainerDetails.setNavigationOnClickListener {
            requireActivity().onBackPressed()
        }

        // show textview telling to do a long press in order to delete the container
        binding.btnContainerDelete.root.setOnClickListener {
            val markwon = Markwon.builder(requireContext()).build()

            markwon.setMarkdown(binding.tvBtnRemoveContainerDescription,
                getString(R.string.deletingContainerNote).replace("{containerName}",
                    selectedContainer.name))

            binding.tvBtnRemoveContainerDescription.visibility = View.VISIBLE
        }

        // on long press - delete the docker container and go back to docker lister
        binding.btnContainerDelete.root.setOnLongClickListener {
            val fullUrl =
                getString(R.string.removeDockerContainer).replace("{baseUrl}",
                    globalVars.currentUser!!.serverUrl.removeSuffix("/"))
                    .replace("{containerId}", selectedContainer.id)
                    .replace("{endpointId}", globalVars.currentUser!!.currentEndpoint.id.toString())

            model.setStateEvent(MainStateEvent.DeleteContaier(globalVars.currentUser!!.jwt!!,
                fullUrl,
                globalVars.currentUser!!.isUsingApiKey,
                selectedContainer))

            true
        }

        subscribeObserver(model, globActivity, btnDeleteState)
    }

    private fun subscribeObserver(
        dataViewModel: DockerListerViewModel,
        mainActivity: MainActiviy,
        btnDeleteState: BtnDeleteContainer,
    ) {
        dataViewModel.dataState.observe(viewLifecycleOwner, { ds ->
            when (ds) {
                is DataState.Success<List<Kontainer>> -> {
                    isDeletingNow = false

                    findNavController().popBackStack()
                }
                is DataState.Error -> {
                    isDeletingNow = false
                    btnDeleteState.changeBtnState(true)

                    mainActivity.showGenericSnack(requireContext(),
                        requireView(),
                        "Error deleting container! Please try again.",
                        R.color.white,
                        R.color.orange_warning)
                }
                is DataState.DeleteLoading -> {
                    isDeletingNow = true
                    btnDeleteState.changeBtnState(false)
                    binding.tvBtnRemoveContainerDescription.visibility = View.GONE
                }
                is DataState.None -> {
                }

                else -> {}
            }
        })
    }

    private fun setContainerDetails(container: Kontainer) {
        val markwon = Markwon.builder(requireContext())
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder
                        .codeTextColor(ContextCompat.getColor(requireContext(), R.color.blue_main))
                        .linkColor(ContextCompat.getColor(requireContext(), R.color.teal_200))
                }
            })
            .usePlugin(LinkifyPlugin.create(Linkify.EMAIL_ADDRESSES or Linkify.WEB_URLS))
            .build()

        val id = "### ID\n- *${container.id}*\n"

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
        val createdDate = formatter.format(ofEpochSecond(container.created))
        val formattedDate = "### Date Created\n- ${createdDate}\n"

        val imageName = "### Image\n- ${container.pulledImage}\n"

        val maintainer: String = container.maintainerInfo.maintainer?.let {
            val maintainerUrl: String =
                container.maintainerInfo.url?.let { url -> "- url: ${url}\n" } ?: ""
            "### Maintainer Info\n- name: ${container.maintainerInfo.maintainer}\n${maintainerUrl}"
        } ?: ""

        val hostConfig = "### Host Config\n- Network Mode: `${container.hostConfig.networkMode}`\n"

        val allMounts: String =
            if (container.mounts.isNotEmpty() && container.mounts.any { m -> m.type == "bind" }) {
                val mount = "### Mounts [*External* : *Internal*]\n"
                val mounts: String = container.mounts.filter { m -> m.type == "bind" }.map { m ->
                    "- `${m.source}`:`${m.destination}`\n"
                }.joinToString("\n")

                "$mount$mounts"
            } else
                ""

        val allPorts: String =
            if (container.ports.isNotEmpty()) {
                val port = "### Ports [*PublicPort* : *PrivatePort* - protocol]\n"
                val ports: String = container.ports.map { p ->
                    "> `${p.publicPort.let { it } ?: "none"}`:`${p.privatePort}` - **${p.type}**\n"
                }.distinct().joinToString("\n")

                "$port$ports"
            } else
                ""

        val state = "### State\n- ***${
            container.state.name.lowercase(getDefault())
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(getDefault()) else it.toString() }
        }***\n"

        val status = "### Status\n- ***${
            container.status.lowercase(getDefault())
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(getDefault()) else it.toString() }
        }***\n"

        val completeInfo =
            StringBuilder().append(id).append(formattedDate).append(imageName).append(maintainer)
                .append(hostConfig).append(allMounts).append(allPorts).append(state).append(status)
        markwon.setMarkdown(binding.tvContainerDetailsInfo,
            "${completeInfo}")

    }
}
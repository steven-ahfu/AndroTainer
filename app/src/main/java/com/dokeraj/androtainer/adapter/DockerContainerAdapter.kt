package com.dokeraj.androtainer.adapter

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment.Companion.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dokeraj.androtainer.DockerListerFragment
import com.dokeraj.androtainer.DockerListerFragmentDirections
import com.dokeraj.androtainer.R
import com.dokeraj.androtainer.databinding.DockerCardItemBinding
import com.dokeraj.androtainer.globalvars.GlobalApp
import com.dokeraj.androtainer.models.ContainerActionType
import com.dokeraj.androtainer.models.ContainerStateType
import com.dokeraj.androtainer.models.Kontainer
import com.dokeraj.androtainer.models.KontainerStats
import com.google.android.material.card.MaterialCardView
import com.dokeraj.androtainer.viewmodels.DockerListerViewModel
import com.dokeraj.androtainer.viewmodels.MainStateEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.Locale

class DockerContainerAdapter(
    private val baseUrl: String,
    private val jwt: String,
    private val isUsingApiKey: Boolean,
    private val endpointId: Int,
    private val globalApp: GlobalApp,
    private val context: Context,
    private val frag: DockerListerFragment,
    private val dataViewModel: DockerListerViewModel,
) : ListAdapter<Kontainer, DockerContainerAdapter.ContainerViewHolder>(KontainerDiffCallback()) {

    private var statsById: Map<String, KontainerStats> = emptyMap()

    /** push fresh stats and rebind only the stats line (payload avoids card flicker) */
    fun updateStats(stats: Map<String, KontainerStats>) {
        statsById = stats
        notifyItemRangeChanged(0, itemCount, PAYLOAD_STATS)
    }

    override fun onBindViewHolder(
        holder: ContainerViewHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        if (payloads.contains(PAYLOAD_STATS)) {
            bindStats(holder, getItem(position))
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    private fun bindStats(holder: ContainerViewHolder, item: Kontainer) {
        if (item.state != ContainerStateType.RUNNING) {
            holder.cardStatsView.visibility = View.GONE
            return
        }
        holder.cardStatsView.visibility = View.VISIBLE
        val stats = statsById[item.id]
        holder.cardStatsView.text = if (stats != null)
            String.format(Locale.US, "CPU %.1f%%   •   Memory %.0f MiB (%.1f%%)",
                stats.cpuPct, stats.memUsedMib, stats.memPct)
        else
            "CPU —   •   Memory —"
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContainerViewHolder {
        val binding = DockerCardItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ContainerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContainerViewHolder, position: Int) {
        val currentItem: Kontainer = getItem(position)

        holder.dockerNameView.text = currentItem.name

        when (currentItem.state) {
            ContainerStateType.RUNNING -> {
                /** set style for running docker container */
                setCardStyle(
                    statusTextColor = R.color.disText1,
                    cardBckColor = R.color.disGreen,
                    statusIconImage = R.drawable.ic_docker_status,
                    statusIconColor = R.color.disText1,
                    currentItemNum = position,
                    holder = holder
                )
            }
            ContainerStateType.EXITED -> {
                /** set style for stopped docker container */
                setCardStyle(
                    statusTextColor = R.color.disText1,
                    cardBckColor = R.color.disRed,
                    statusIconImage = R.drawable.ic_docker_status,
                    statusIconColor = R.color.disText1,
                    currentItemNum = position,
                    holder = holder
                )
            }
            ContainerStateType.TRANSITIONING -> {
                /** set style for container that is either starting or stopping */
                setCardStyle(
                    statusTextColor = R.color.disText1,
                    cardBckColor = R.color.dis6,
                    statusIconImage = R.drawable.ic_docker_status,
                    statusIconColor = R.color.disText1,
                    currentItemNum = position,
                    holder = holder
                )
            }
            ContainerStateType.ERRORED -> {
                /** set style for docker container that has received error from portainer api */
                setCardStyle(
                    statusTextColor = R.color.disText3,
                    cardBckColor = R.color.disYellow,
                    statusIconImage = R.drawable.ic_warning,
                    statusIconColor = R.color.disText3,
                    currentItemNum = position,
                    holder = holder
                )
            }
            ContainerStateType.CREATED -> {
                /** set style for docker container that is in the created state */
                setCardStyle(
                    statusTextColor = R.color.disText2,
                    cardBckColor = R.color.teal_700,
                    statusIconImage = R.drawable.ic_created,
                    statusIconColor = R.color.disText2,
                    currentItemNum = position,
                    holder = holder
                )
            }

            ContainerStateType.RESTARTING -> {
                /** set style for container that is restarting */
                setCardStyle(
                    statusTextColor = R.color.disText1,
                    cardBckColor = R.color.dis6,
                    statusIconImage = R.drawable.ic_docker_status,
                    statusIconColor = R.color.disText1,
                    currentItemNum = position,
                    holder = holder
                )
            }

            ContainerStateType.PAUSED -> {
                /** set style for paused docker container (unpause lives on the details page) */
                setCardStyle(
                    statusTextColor = R.color.disText1,
                    cardBckColor = R.color.dis5,
                    statusIconImage = R.drawable.ic_docker_status,
                    statusIconColor = R.color.disText1,
                    currentItemNum = position,
                    holder = holder
                )
            }
        }

        holder.cardHolderLayout.setOnClickListener {
            dataViewModel.setStateEvent(MainStateEvent.SetNone)
            val action =
                DockerListerFragmentDirections.actionDockerListerFragmentToDockerContainerDetailsFragment(
                    currentItem)
            findNavController(frag).navigate(action)
        }

        holder.cardHolderLayout.setOnLongClickListener {
            showContainerActions(holder.cardHolderLayout, currentItem)
            true
        }

        bindStats(holder, currentItem)
    }

    class ContainerViewHolder(binding: DockerCardItemBinding) : RecyclerView.ViewHolder(binding.root) {
        val dockerNameView: TextView = binding.etDockerName
        val dockerStatusView: TextView = binding.etDockerStatus
        val cardHolderLayout: MaterialCardView = binding.cardHolderLayout
        val statusIconView: ImageView = binding.statusIcon
        val cardStatsView: TextView = binding.tvCardStats
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun callStartStopContainer(
        containerId: String,
        actionType: ContainerActionType,
    ) {
        val fullUrl = context.getString(R.string.StartStopContainer)
            .replace("{baseUrl}", baseUrl.removeSuffix("/"))
            .replace("{containerId}", containerId)
            .replace("{actionType}", actionType.name.lowercase(Locale.US))
            .replace("{endpointId}", endpointId.toString())

        dataViewModel.setStateEvent(MainStateEvent.StartStopKontejneri(jwt = jwt,
            url = fullUrl,
            isUsingApiKey = isUsingApiKey,
            containerId = containerId,
            containerActionType = actionType))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun callRestartContainer(
        containerId: String,
    ) {
        val fullUrl = context.getString(R.string.RestartContainer)
            .replace("{baseUrl}", baseUrl.removeSuffix("/"))
            .replace("{containerId}", containerId)
            .replace("{endpointId}", endpointId.toString())

        dataViewModel.setStateEvent(MainStateEvent.RestartKontejneri(jwt = jwt,
            url = fullUrl,
            isUsingApiKey = isUsingApiKey,
            containerId = containerId))
    }

    private fun callPauseContainer(containerId: String, isPause: Boolean) {
        val action = if (isPause) "pause" else "unpause"
        val fullUrl = context.getString(R.string.pauseUnpauseContainer)
            .replace("{baseUrl}", baseUrl.removeSuffix("/"))
            .replace("{containerId}", containerId)
            .replace("{pauseAction}", action)
            .replace("{endpointId}", endpointId.toString())

        dataViewModel.setStateEvent(MainStateEvent.PauseKontejneri(
            jwt = jwt,
            url = fullUrl,
            isUsingApiKey = isUsingApiKey,
            containerId = containerId,
            isPause = isPause,
        ))
    }

    private fun showContainerActions(anchor: View, container: Kontainer) {
        val popupContext = ContextThemeWrapper(
            context,
            R.style.ThemeOverlay_AndroTainer_PopupMenu,
        )
        val popup = PopupMenu(popupContext, anchor)
        when (container.state) {
            ContainerStateType.RUNNING -> {
                popup.menu.add(0, MENU_START_STOP, 0, R.string.action_stop)
                popup.menu.add(0, MENU_RESTART, 1, R.string.action_restart)
                popup.menu.add(0, MENU_PAUSE, 2, R.string.action_pause)
            }
            ContainerStateType.PAUSED ->
                popup.menu.add(0, MENU_PAUSE, 0, R.string.action_unpause)
            ContainerStateType.EXITED, ContainerStateType.CREATED ->
                popup.menu.add(0, MENU_START_STOP, 0, R.string.action_start)
            else -> Unit
        }
        popup.menu.add(0, MENU_LOGS, 10, R.string.action_open_logs)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_START_STOP -> callStartStopContainer(
                    container.id,
                    if (container.state == ContainerStateType.RUNNING)
                        ContainerActionType.STOP else ContainerActionType.START,
                )
                MENU_RESTART -> callRestartContainer(container.id)
                MENU_PAUSE -> callPauseContainer(
                    container.id,
                    container.state != ContainerStateType.PAUSED,
                )
                MENU_LOGS -> {
                    val action =
                        DockerListerFragmentDirections.actionDockerListerFragmentToDockerLogging(
                            container.id,
                            container.name,
                        )
                    findNavController(frag).navigate(action)
                }
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        popup.show()
    }

    private fun setCardStyle(
        statusTextColor: Int,
        cardBckColor: Int,
        statusIconImage: Int,
        statusIconColor: Int,
        currentItemNum: Int,
        holder: ContainerViewHolder,
    ) {
        val currentItem = getItem(currentItemNum)

        if (holder.dockerNameView.text.toString() == currentItem.name) {
            holder.cardHolderLayout.setCardBackgroundColor(
                ContextCompat.getColor(context, cardBckColor))
            holder.cardHolderLayout.strokeColor =
                ContextCompat.getColor(context, statusIconColor)

            // statusView text and color
            holder.dockerStatusView.text =
                currentItem.status.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            holder.dockerStatusView.setTextColor(ContextCompat.getColor(context,
                statusTextColor))

            // Status Icon
            holder.statusIconView.setImageResource(statusIconImage)
            holder.statusIconView.setColorFilter(ContextCompat.getColor(context,
                statusIconColor))

        }
    }

    fun areItemsInTransitioningState(): Boolean {
        return currentList.any { pCont -> pCont.state == ContainerStateType.TRANSITIONING }
    }

    companion object {
        private const val PAYLOAD_STATS = "payload_stats"
        private const val MENU_START_STOP = 1
        private const val MENU_RESTART = 2
        private const val MENU_PAUSE = 3
        private const val MENU_LOGS = 4
    }
}

class KontainerDiffCallback : DiffUtil.ItemCallback<Kontainer>() {
    override fun areItemsTheSame(oldItem: Kontainer, newItem: Kontainer): Boolean = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Kontainer, newItem: Kontainer): Boolean = oldItem == newItem
}

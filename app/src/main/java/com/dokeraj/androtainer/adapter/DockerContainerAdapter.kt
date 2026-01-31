package com.dokeraj.androtainer.adapter

import android.content.Context
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
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
                setCardStyle(containerState = ContainerStateType.RUNNING,
                    statusTextColor = R.color.disText1,
                    cardBckColor = R.color.disGreen,
                    buttonText = "STOP",
                    buttonIsEnabled = true,
                    buttonColor = R.color.blue_main,
                    statusIconImage = R.drawable.ic_docker_status,
                    statusIconColor = R.color.disText1,
                    currentItemNum = position,
                    holder = holder
                )
            }
            ContainerStateType.EXITED -> {
                /** set style for stopped docker container */
                setCardStyle(containerState = ContainerStateType.EXITED,
                    statusTextColor = R.color.disText1,
                    cardBckColor = R.color.disRed,
                    buttonText = "START",
                    buttonIsEnabled = true,
                    buttonColor = R.color.blue_main,
                    statusIconImage = R.drawable.ic_docker_status,
                    statusIconColor = R.color.disText1,
                    currentItemNum = position,
                    holder = holder
                )
            }
            ContainerStateType.TRANSITIONING -> {
                /** set style for container that is either starting or stopping */
                setCardStyle(containerState = ContainerStateType.TRANSITIONING,
                    statusTextColor = R.color.disText1,
                    cardBckColor = R.color.dis6,
                    buttonText = currentItem.status,
                    buttonIsEnabled = false,
                    buttonColor = R.color.dis6,
                    statusIconImage = R.drawable.ic_docker_status,
                    statusIconColor = R.color.disText1,
                    currentItemNum = position,
                    holder = holder
                )
            }
            ContainerStateType.ERRORED -> {
                /** set style for docker container that has received error from portainer api */
                setCardStyle(containerState = ContainerStateType.ERRORED,
                    statusTextColor = R.color.disText3,
                    cardBckColor = R.color.disYellow,
                    buttonText = "ERROR",
                    buttonIsEnabled = false,
                    buttonColor = R.color.disYellow,
                    statusIconImage = R.drawable.ic_warning,
                    statusIconColor = R.color.disText3,
                    currentItemNum = position,
                    holder = holder
                )
            }
            ContainerStateType.CREATED -> {
                /** set style for docker container that is in the created state */
                setCardStyle(containerState = ContainerStateType.CREATED,
                    statusTextColor = R.color.disText2,
                    cardBckColor = R.color.teal_700,
                    buttonText = "START",
                    buttonIsEnabled = true,
                    buttonColor = R.color.blue_main,
                    statusIconImage = R.drawable.ic_created,
                    statusIconColor = R.color.disText2,
                    currentItemNum = position,
                    holder = holder
                )
            }

            ContainerStateType.RESTARTING -> {
                /** set style for container that is restarting */
                setCardStyle(containerState = ContainerStateType.RESTARTING,
                    statusTextColor = R.color.disText1,
                    cardBckColor = R.color.dis6,
                    buttonText = currentItem.status,
                    buttonTextSize = 10f,
                    buttonIsEnabled = false,
                    buttonColor = R.color.dis6,
                    statusIconImage = R.drawable.ic_docker_status,
                    statusIconColor = R.color.disText1,
                    currentItemNum = position,
                    holder = holder
                )
            }
        }

        holder.dockerButton.setOnClickListener {
            callStartStopContainer(currentItemIndex = position,
                containerId = currentItem.id,
                actionType = if (getItem(position).state == ContainerStateType.RUNNING) ContainerActionType.STOP else ContainerActionType.START)
        }

        // restart the container
        holder.dockerButton.setOnLongClickListener {
            callRestartContainer(currentItemIndex = position,
                containerId = currentItem.id)

            true
        }

        holder.cardHolderLayout.setOnClickListener {
            dataViewModel.setStateEvent(MainStateEvent.SetNone)
            val action =
                DockerListerFragmentDirections.actionDockerListerFragmentToDockerContainerDetailsFragment(
                    currentItem)
            findNavController(frag).navigate(action)
        }

        holder.cardHolderLayout.setOnLongClickListener {
            val action = DockerListerFragmentDirections.actionDockerListerFragmentToDockerLogging(
                currentItem.id,
                currentItem.name)
            findNavController(frag).navigate(action)
            true
        }

    }

    class ContainerViewHolder(binding: DockerCardItemBinding) : RecyclerView.ViewHolder(binding.root) {
        val dockerNameView: TextView = binding.etDockerName
        val dockerStatusView: TextView = binding.etDockerStatus
        val dockerButton: View = binding.btnStartStop.root
        val btnBackgroundView: ConstraintLayout = itemView.findViewById(R.id.clLister)
        val btnProgressBar: ProgressBar = itemView.findViewById(R.id.pbLister)
        val btnTextView: TextView = itemView.findViewById(R.id.tvLister)
        val cardHolderLayout: ConstraintLayout = binding.cardHolderLayout
        val statusIconView: ImageView = binding.statusIcon
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun callStartStopContainer(
        currentItemIndex: Int,
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
            currentItem = currentItemIndex,
            containerActionType = actionType))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun callRestartContainer(
        currentItemIndex: Int,
        containerId: String,
    ) {
        val fullUrl = context.getString(R.string.RestartContainer)
            .replace("{baseUrl}", baseUrl.removeSuffix("/"))
            .replace("{containerId}", containerId)
            .replace("{endpointId}", endpointId.toString())

        dataViewModel.setStateEvent(MainStateEvent.RestartKontejneri(jwt = jwt,
            url = fullUrl,
            isUsingApiKey = isUsingApiKey,
            currentItem = currentItemIndex))
    }

    private fun setCardStyle(
        containerState: ContainerStateType,
        statusTextColor: Int,
        cardBckColor: Int,
        buttonText: String,
        buttonTextSize: Float = 12f,
        buttonIsEnabled: Boolean,
        buttonColor: Int,
        statusIconImage: Int,
        statusIconColor: Int,
        currentItemNum: Int,
        holder: ContainerViewHolder,
    ) {
        val currentItem = getItem(currentItemNum)

        if (holder.dockerNameView.text.toString() == currentItem.name) {
            // change cardHolderLayout background
            holder.cardHolderLayout.background.colorFilter =
                BlendModeColorFilter(ContextCompat.getColor(context,
                    cardBckColor), BlendMode.SRC)

            // statusView text and color
            holder.dockerStatusView.text =
                currentItem.status.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            holder.dockerStatusView.setTextColor(ContextCompat.getColor(context,
                statusTextColor))

            // change button background
            holder.btnTextView.text = buttonText
            holder.btnTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, buttonTextSize)
            holder.dockerButton.isClickable = buttonIsEnabled
            holder.dockerButton.isEnabled = buttonIsEnabled
            val btnBackground = holder.btnBackgroundView.background
            btnBackground.mutate()
            btnBackground.colorFilter =
                BlendModeColorFilter(ContextCompat.getColor(context,
                    buttonColor), BlendMode.SRC)
            holder.btnBackgroundView.background = btnBackground

            // Status Icon
            holder.statusIconView.setImageResource(statusIconImage)
            holder.statusIconView.setColorFilter(ContextCompat.getColor(context,
                statusIconColor))

            // Progress Bar
            holder.btnProgressBar.visibility =
                if (containerState == ContainerStateType.TRANSITIONING || containerState == ContainerStateType.RESTARTING) View.VISIBLE else View.GONE
        }
    }

    fun areItemsInTransitioningState(): Boolean {
        return currentList.any { pCont -> pCont.state == ContainerStateType.TRANSITIONING }
    }
}

class KontainerDiffCallback : DiffUtil.ItemCallback<Kontainer>() {
    override fun areItemsTheSame(oldItem: Kontainer, newItem: Kontainer): Boolean = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Kontainer, newItem: Kontainer): Boolean = oldItem == newItem
}
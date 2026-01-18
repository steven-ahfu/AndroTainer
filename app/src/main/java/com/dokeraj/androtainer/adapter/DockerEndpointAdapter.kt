package com.dokeraj.androtainer.adapter

import android.content.Context
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.RecyclerView
import com.dokeraj.androtainer.DockerListerFragment
import com.dokeraj.androtainer.MainActiviy
import com.dokeraj.androtainer.R
import com.dokeraj.androtainer.databinding.DockerEndpointsCardItemBinding
import com.dokeraj.androtainer.globalvars.GlobalApp
import com.dokeraj.androtainer.models.DockerEndpoint
import com.dokeraj.androtainer.viewmodels.DockerListerViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi

class DockerEndpointAdapter(
    private val dockerEndpoints: List<DockerEndpoint>, val globalApp: GlobalApp,
    private val context: Context,
    val globActivity: MainActiviy,
    val drawerLayout: DrawerLayout,
    val model: DockerListerViewModel,
    val dockerListerAdapter: DockerContainerAdapter,
    val dockerListerFragment: DockerListerFragment,
) :
    RecyclerView.Adapter<DockerEndpointAdapter.DockerEndpointViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DockerEndpointViewHolder {
        val binding = DockerEndpointsCardItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DockerEndpointViewHolder(binding)
    }

    @ExperimentalCoroutinesApi
    override fun onBindViewHolder(holder: DockerEndpointViewHolder, position: Int) {
        val currentItem = dockerEndpoints[position]

        holder.tvName.text = currentItem.name
        holder.tvUrl.text = currentItem.url

        if (globalApp.currentUser!!.currentEndpoint.id == currentItem.id) {
            setCardStyle(R.color.disText2, R.color.disGreen, holder)
        } else {
            setCardStyle(R.color.loggingText, R.color.dis1, holder)
        }

        holder.llEndpoint.setOnClickListener {
            val newUser = globalApp.currentUser!!.copy(currentEndpoint = currentItem)
            globActivity.setGlobalCredentials(newUser)
            notifyDataSetChanged()
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
                dockerListerFragment.callSwiperLogic(model,
                    globActivity,
                    globalApp,
                    dockerListerAdapter)
            }
        }
    }

    override fun getItemCount(): Int = dockerEndpoints.size

    class DockerEndpointViewHolder(
        binding: DockerEndpointsCardItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        val tvName: TextView = binding.tvDockerEnpointNameCard
        val tvUrl: TextView = binding.tvDockerEnpointUrlCard
        val llEndpoint: LinearLayout = binding.llDockerEndpoint
    }

    private fun setCardStyle(
        textColor: Int,
        bckColor: Int,
        holder: DockerEndpointViewHolder,
    ) {
        holder.llEndpoint.background.colorFilter =
            BlendModeColorFilter(ContextCompat.getColor(context,
                bckColor), BlendMode.SRC)

        holder.tvName.setTextColor(ContextCompat.getColor(context,
            textColor))
        holder.tvUrl.setTextColor(ContextCompat.getColor(context,
            textColor))
    }
}
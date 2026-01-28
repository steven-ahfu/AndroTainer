package com.dokeraj.androtainer.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.RecyclerView
import com.dokeraj.androtainer.MainActiviy
import com.dokeraj.androtainer.R
import com.dokeraj.androtainer.databinding.UsersCardItemBinding
import com.dokeraj.androtainer.models.Credential

class UsersLoginAdapter(
    private val credentials: List<Credential>,
    val usersDrawerLayout: DrawerLayout,
    val homeView: View,
    val mainActiviy: MainActiviy,
    val context: Context,
) :
    RecyclerView.Adapter<UsersLoginAdapter.UsersViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UsersViewHolder {
        val binding = UsersCardItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return UsersViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UsersViewHolder, position: Int) {
        val currentItem: Credential = credentials[position]

        holder.serverUrl.text = currentItem.serverUrl
        holder.username.text = currentItem.username
        if (currentItem.isUsingApiKey)
            holder.userIsApiKey.visibility = View.VISIBLE
        else
            holder.userIsApiKey.visibility = View.GONE

        holder.cardLayout.setOnClickListener {
            val etUrl: TextView = homeView.findViewById(R.id.etUrl)
            val etUser: TextView = homeView.findViewById(R.id.etUser)
            val etPass: TextView = homeView.findViewById(R.id.etPass)
            val switchApiKey: SwitchCompat = homeView.findViewById(R.id.swUseApiKey)
            val etApiKey: TextView = homeView.findViewById(R.id.etApiKey)

            etUrl.text = currentItem.serverUrl

            if (currentItem.isUsingApiKey) {
                switchApiKey.isChecked = true
                etApiKey.text = currentItem.jwt
            } else {
                switchApiKey.isChecked = false
                etUser.text = currentItem.username
                etPass.text = currentItem.pwd
            }

            mainActiviy.showGenericSnack(context,homeView,"Loaded `${currentItem.username}` credentials!", R.color.blue_main, R.color.dis3)
            usersDrawerLayout.close()
        }
    }

    override fun getItemCount() = credentials.size

    class UsersViewHolder(
        val binding: UsersCardItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        val serverUrl: TextView = binding.tvServerUrl
        val username: TextView = binding.tvCardUsername
        val cardLayout: ConstraintLayout = binding.usersCardHolderLayout
        val userIsApiKey: TextView = binding.tvIsUserApiKey
    }
}
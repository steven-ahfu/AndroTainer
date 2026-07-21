package com.dokeraj.androtainer

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Patterns
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.activity.addCallback
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.dokeraj.androtainer.adapter.UsersLoginAdapter
import com.dokeraj.androtainer.buttons.BtnLogin
import com.dokeraj.androtainer.databinding.FragmentHomeBinding
import com.dokeraj.androtainer.globalvars.GlobalApp
import com.dokeraj.androtainer.interfaces.ApiInterface
import com.dokeraj.androtainer.interfaces.ApiInterfaceApiKey
import com.dokeraj.androtainer.models.Credential
import com.dokeraj.androtainer.models.DockerEndpoint
import com.dokeraj.androtainer.models.Kontainer
import com.dokeraj.androtainer.models.Kontainers
import com.dokeraj.androtainer.models.retrofit.DockerEndpointNetworkMapper
import com.dokeraj.androtainer.models.retrofit.Jwt
import com.dokeraj.androtainer.models.retrofit.PEndpointsResponse
import com.dokeraj.androtainer.models.retrofit.UserCredentials
import com.dokeraj.androtainer.network.RetrofitInstance
import com.dokeraj.androtainer.util.DataState
import com.dokeraj.androtainer.viewmodels.HomeFragmentViewModel
import com.dokeraj.androtainer.viewmodels.HomeMainStateEvent
import dagger.hilt.android.AndroidEntryPoint
import retrofit2.Call
import retrofit2.Response
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Locale.getDefault
import kotlin.math.abs

@AndroidEntryPoint
class HomeFragment : Fragment(R.layout.fragment_home) {
    var disableDrawerSwipe: Boolean = false

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    /** how to instantiate a viewModel object*/
    private val model: HomeFragmentViewModel by viewModels()

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentHomeBinding.bind(view)

        @Suppress("DEPRECATION")
        requireActivity().window.statusBarColor =
            ContextCompat.getColor(requireContext(), R.color.dis4)

        val detector = GestureDetector(requireContext(), UsersGestureListener())

        val globActivity: MainActiviy = (activity as MainActiviy?)!!
        val globalVars = (globActivity.application as GlobalApp)

        binding.swUseApiKey.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.lnLayUsrPwd.visibility = View.INVISIBLE
                binding.conApiKey.visibility = View.VISIBLE
            } else {
                binding.lnLayUsrPwd.visibility = View.VISIBLE
                binding.conApiKey.visibility = View.GONE
            }
        }

        val btnLoginState = BtnLogin(requireContext(), binding.lgnBtn)

        if (globActivity.getLogoutMsg() != null) {
            globActivity.showGenericSnack(requireContext(),
                requireView(),
                globActivity.getLogoutMsg()!!,
                R.color.white,
                R.color.orange_warning)
            globActivity.setLogoutMsg(null)
        }

        globalVars.currentUser?.serverUrl?.let { binding.etUrl.setText(it) }
        globalVars.currentUser?.username?.let { binding.etUser.setText(it) }
        globalVars.currentUser?.pwd?.let { binding.etPass.setText(it) }
        globalVars.currentUser?.isUsingApiKey?.let { useApi ->
            binding.swUseApiKey.isChecked = useApi
            globalVars.currentUser?.jwt?.let { binding.etApiKey.setText(it) }
        }


        if (globActivity.hasJwt() && (globActivity.isJwtValid())) {
            btnLoginState.changeBtnState(false)
            callGetContainers(globalVars.currentUser!!.serverUrl,
                globalVars.currentUser!!.jwt!!,
                globalVars.currentUser!!.currentEndpoint.id, globalVars.currentUser!!.isUsingApiKey)
        } else if (globActivity.hasJwt() && !globActivity.isJwtValid()) {
            btnLoginState.changeBtnState(false)
            if (globActivity.isUserUsingApiKey()) {
                authenticateApi(binding.etUrl.text.toString(),
                    binding.etApiKey.text.toString(),
                    btnLoginState)
            } else
                authenticate(binding.etUrl.text.toString(),
                    binding.etUser.text.toString(),
                    binding.etPass.text.toString(),
                    btnLoginState)
        }

        view.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
        }

        // on button back pressed - close the users drawer
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, true) {
            binding.usersLister.closeDrawer(GravityCompat.START)
        }

        // load the user credentials into the drawer recyclerview
        val savedUsers: List<Credential> = globalVars.credentials.map { (_, v) -> v }

        if (savedUsers.isNotEmpty()) {
            binding.tvUsersNoContent.visibility = View.GONE
            val recyclerAdapter =
                UsersLoginAdapter(savedUsers, binding.usersLister, view, globActivity, requireContext())
            binding.rvLoginUsers.adapter = recyclerAdapter
            binding.rvLoginUsers.layoutManager = LinearLayoutManager(activity)
            binding.rvLoginUsers.setHasFixedSize(true)
        } else {
            binding.rvLoginUsers.visibility = View.GONE
            binding.tvUsersNoContent.visibility = View.VISIBLE
        }


        binding.lgnBtn.root.setOnClickListener {
            disableDrawerSwipe = true
            btnLoginState.changeBtnState(false)
            val baseUrl = binding.etUrl.text.toString().trim()
            val isValidUrl = Patterns.WEB_URL.matcher(baseUrl).matches() &&
                    (baseUrl.lowercase(getDefault()).startsWith("http://") ||
                            baseUrl.lowercase(getDefault()).startsWith("https://"))

            if (!isValidUrl) {
                val errText = if (!baseUrl.lowercase(getDefault()).startsWith("http://") &&
                    !baseUrl.lowercase(getDefault()).startsWith("https://"))
                    "The URL must start with http:// or https://"
                else
                    "Invalid URL!"

                disableDrawerSwipe = false
                btnLoginState.changeBtnState(true)
                globActivity.showGenericSnack(requireContext(),
                    requireView(),
                    errText,
                    R.color.white,
                    R.color.orange_warning)
            } else if (binding.swUseApiKey.isChecked) {
                val apiKey = binding.etApiKey.text.toString().trim()
                if (apiKey.isEmpty()) {
                    onLoginError(btnLoginState, "API key cannot be empty!")
                    return@setOnClickListener
                }
                authenticateApi(baseUrl,
                    apiKey,
                    btnLoginState)
            } else {
                authenticate(baseUrl,
                    binding.etUser.text.toString(),
                    binding.etPass.text.toString(), btnLoginState)
            }
        }

        subscribeObservers(btnLoginState, globActivity)
    }

    private fun subscribeObservers(btnLoginState: BtnLogin, mainActivity: MainActiviy) {
        model.dataState.observe(viewLifecycleOwner, { ds ->
            when (ds) {
                is DataState.Success<List<Kontainer>> -> {

                    mainActivity.setIsLoginToDockerLister(true)
                    val action =
                        HomeFragmentDirections.actionHomeFragmentToDockerListerFragment(
                            Kontainers(ds.data))
                    findNavController().navigate(action)

                }
                is DataState.Error -> {
                    disableDrawerSwipe = false
                    btnLoginState.changeBtnState(true)
                    mainActivity.showGenericSnack(requireContext(),
                        requireView(),
                        "Cannot pull docker containers!",
                        R.color.red,
                        R.color.white)
                }
                is DataState.Loading -> {
                    disableDrawerSwipe = true
                    btnLoginState.changeBtnState(false)
                }

                else -> {}
            }
        })
    }

    private fun authenticate(
        baseUrl: String,
        usr: String,
        pwd: String,
        btnLoginState: BtnLogin,
    ) {
        val cred = UserCredentials(usr, pwd)

        val fullPath =
            getString(R.string.authenticate).replace("{baseUrl}", baseUrl.removeSuffix("/"))
        val api = RetrofitInstance.retrofitInstance!!.create(ApiInterface::class.java)
        api.loginRequest(cred, fullPath)
            .enqueue(object : retrofit2.Callback<Jwt?> {
                override fun onResponse(
                    call: retrofit2.Call<Jwt?>,
                    response: retrofit2.Response<Jwt?>,
                ) {
                    if (response.isSuccessful) {
                        val jwtResponse = response.body()?.jwt?.takeIf { it.isNotBlank() }

                        if (jwtResponse != null) {
                            val jwtValidUntil: Long =
                                ZonedDateTime.now(ZoneOffset.UTC).plusHours(7).plusMinutes(59)
                                    .toInstant()
                                    .toEpochMilli()

                            getEndpointId(baseUrl = baseUrl,
                                usr = usr,
                                pwd = pwd,
                                btnLoginState = btnLoginState,
                                jwt = jwtResponse,
                                jwtValidUntil = jwtValidUntil,
                                isUsingApiKey = false)
                        } else {
                            onLoginError(btnLoginState,
                                "Portainer returned an empty authentication token!")
                        }
                    } else {
                        showResponseSnack(response.code().toString(), btnLoginState)
                    }
                }

                override fun onFailure(call: retrofit2.Call<Jwt?>, t: Throwable) {
                    onLoginError(btnLoginState, "Server not permitting communication! ${t.message}")
                }
            })
    }

    private fun authenticateApi(
        baseUrl: String,
        apiKey: String,
        btnLoginState: BtnLogin,
    ) {
        val usr = apiKey.take(3) + ".." + apiKey.takeLast(3)
        getEndpointId(baseUrl = baseUrl,
            usr = usr,
            pwd = "",
            btnLoginState = btnLoginState,
            jwt = apiKey,
            jwtValidUntil = 0L,
            isUsingApiKey = true)
    }

    private fun getEndpointId(
        baseUrl: String,
        usr: String,
        pwd: String,
        btnLoginState: BtnLogin,
        jwt: String,
        jwtValidUntil: Long,
        isUsingApiKey: Boolean,
    ) {
        fun getDockerEndpoints(response: PEndpointsResponse): Pair<DockerEndpoint?, List<DockerEndpoint>> {
            val dockerEndpoints: List<DockerEndpoint> =
                DockerEndpointNetworkMapper.mapFromRetrofitModel(response)

            val dockerSock: DockerEndpoint? = dockerEndpoints.find {
                it.url == "unix:///var/run/docker.sock"
            }

            val sortedById: List<DockerEndpoint> = dockerEndpoints.sortedBy { it.id }

            return Pair(dockerSock ?: sortedById.getOrNull(0), sortedById)
        }


        val fullPath =
            getString(R.string.getEnpointId).replace("{baseUrl}", baseUrl.removeSuffix("/"))

        val callback = object : retrofit2.Callback<PEndpointsResponse> {
                override fun onResponse(
                    call: Call<PEndpointsResponse>,
                    response: Response<PEndpointsResponse>,
                ) {
                    disableDrawerSwipe = false

                    if (response.code() == 200 && response.body() != null) {
                        val dockerEndpoints: Pair<DockerEndpoint?, List<DockerEndpoint>> =
                            getDockerEndpoints(response = response.body()!!)

                        if (dockerEndpoints.first != null) {
                            val globActivity: MainActiviy = (activity as MainActiviy?)!!
                            globActivity.setGlobalCredentials(Credential(serverUrl = baseUrl,
                                username = usr,
                                pwd = pwd,
                                jwt = jwt,
                                jwtValidUntil = jwtValidUntil,
                                currentEndpoint = dockerEndpoints.first!!,
                                listOfEndpoints = dockerEndpoints.second,
                                isUsingApiKey = isUsingApiKey),
                                true)

                            callGetContainers(baseUrl,
                                jwt,
                                dockerEndpoints.first!!.id,
                                isUsingApiKey)
                        } else {
                            onLoginError(btnLoginState,
                                "There are no Portainer endpoints listed!")
                        }
                    } else {
                        onLoginError(btnLoginState,
                            "Cannot get portainer endpoint id! Error code: ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<PEndpointsResponse>, t: Throwable) {
                    onLoginError(btnLoginState,
                        "Failed to get Portainer endpoint ID! ${t.message.orEmpty()}")
                }
            }

        if (isUsingApiKey) {
            RetrofitInstance.retrofitInstance!!
                .create(ApiInterfaceApiKey::class.java)
                .getEnpointId(fullPath, jwt, 10, 0)
                .enqueue(callback)
        } else {
            RetrofitInstance.retrofitInstance!!
                .create(ApiInterface::class.java)
                .getEnpointId(fullPath, "Bearer $jwt", 10, 0)
                .enqueue(callback)
        }

    }

    private fun callGetContainers(
        url: String,
        jwt: String,
        endpointId: Int,
        isUsingApiKey: Boolean,
    ) {
        val fullUrl =
            getString(R.string.getDockerContainers).replace("{baseUrl}", url.removeSuffix("/"))
                .replace("{endpointId}", endpointId.toString())

        model.setStateEvent(HomeMainStateEvent.GetosKontejneri(jwt = jwt,
            url = fullUrl,
            isUsingApiKey = isUsingApiKey))
    }

    fun showResponseSnack(responseStatus: String, btnLoginState: BtnLogin) {
        when (responseStatus) {
            "502", "404" -> {
                onLoginError(btnLoginState,
                    "Wrong URL or service is down",
                    R.color.white,
                    R.color.red)
            }
            "422", "401" -> {
                onLoginError(btnLoginState,
                    "Invalid Credentials",
                    R.color.white,
                    R.color.orange_warning)
            }
            else -> {
                onLoginError(btnLoginState,
                    "Server response: Unknown error",
                    R.color.blue_main,
                    R.color.dis4)
            }
        }
    }

    private fun onLoginError(
        btnLoginState: BtnLogin,
        msg: String,
        textColor: Int = R.color.red,
        bckColor: Int = R.color.white,
    ) {
        disableDrawerSwipe = false
        btnLoginState.changeBtnState(true)
        val globActivity: MainActiviy = (activity as MainActiviy?)!!
        globActivity.showGenericSnack(requireContext(),
            requireView(),
            msg,
            textColor,
            bckColor)
    }

    inner class UsersGestureListener : GestureDetector.SimpleOnGestureListener() {
        private val SWIPE_THRESHOLD = 100
        private val SWIPE_VELOCITY_THRESHOLD = 100

        override fun onFling(
            downEvent: MotionEvent?,
            moveEvent: MotionEvent,
            velocityX: Float,
            velocityY: Float,
        ): Boolean {
            if (downEvent == null) return super.onFling(downEvent, moveEvent, velocityX, velocityY)
            
            val diffx = moveEvent.x.minus(downEvent.x)
            val diffy = moveEvent.y.minus(downEvent.y)

            return if (abs(diffx) > abs(diffy)) {
                // this is a left or right swipe
                if (abs(diffx) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    // this is to differentiate between accidental and real swipe
                    if (diffx > 0) {
                        // this is a right swipe (from left to right) - open the drawer
                        openDrawer()
                        true
                    } else if (diffx < 0) {
                        // this is a left swipe - close the drawer
                        binding.usersLister.closeDrawer(GravityCompat.START)
                        true
                    } else
                        super.onFling(downEvent, moveEvent, velocityX, velocityY)
                } else
                    super.onFling(downEvent, moveEvent, velocityX, velocityY)
            } else
                super.onFling(downEvent, moveEvent, velocityX, velocityY)
        }
    }

    private fun openDrawer() {
        if (!disableDrawerSwipe)
            binding.usersLister.openDrawer(GravityCompat.START)
    }
}

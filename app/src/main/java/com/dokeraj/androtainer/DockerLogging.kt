package com.dokeraj.androtainer

import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.dokeraj.androtainer.adapter.LoggingAdapter
import com.dokeraj.androtainer.databinding.FragmentLoggingBinding
import com.dokeraj.androtainer.globalvars.GlobalApp
import com.dokeraj.androtainer.interfaces.ApiInterface
import com.dokeraj.androtainer.interfaces.ApiInterfaceApiKey
import com.dokeraj.androtainer.models.LogItem
import com.dokeraj.androtainer.network.RetrofitInstance
import com.dokeraj.androtainer.util.LogTimer
import dagger.hilt.android.AndroidEntryPoint
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import java.io.Reader
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter


@AndroidEntryPoint
class DockerLogging : Fragment(R.layout.fragment_logging) {
    private var _binding: FragmentLoggingBinding? = null
    private val binding get() = _binding!!
    private val args: DockerLoggingArgs by navArgs()
    private val linesOfLog = listOf(1000, 5000, 100)
    private val autoRefreshIntervals = listOf(3000, 6000, 12000)
    private val eEgg = listOf("Y U Do Dis!?",
        "There is no hidden functionality here!",
        "Stop clicking me!",
        "Aren't you a nosy snowflake :)",
        "I'm warning you!")
    val dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val globActivity: MainActiviy = (activity as MainActiviy?)!!
        val globalVars: GlobalApp = (globActivity.application as GlobalApp)

        _binding = FragmentLoggingBinding.bind(view)

        binding.tbContainerLogging.navigationIcon =
            ContextCompat.getDrawable(requireActivity(), R.drawable.ic_back)

        binding.tvContainerLoggingTitle.text = "${args.containerName} Logs"
        binding.tvLoggingEndpointName.text = globalVars.currentUser!!.currentEndpoint.name

        val contId = args.containerId
        val baseUrl = globalVars.currentUser!!.serverUrl
        val token: String = globalVars.currentUser!!.jwt!!
        val endpointId: Int = globalVars.currentUser!!.currentEndpoint.id
        val isUsingApiKey: Boolean = globalVars.currentUser!!.isUsingApiKey

        // pull from global var and setup the chips state
        initChipsState(globalVars)

        binding.srlLogging.isEnabled = true
        binding.srlLogging.isRefreshing = true

        val timer = LogTimer()

        if (binding.chpAutoRefresh.isChecked) {
            timer.startTimer(this, baseUrl, endpointId, contId, token, globalVars, isUsingApiKey, binding.chpTimestamp.isChecked)
        } else {
            getLogFromRetro(baseUrl,
                contId,
                token,
                endpointId,
                globalVars.logSettings?.linesCount ?: 1000,
                binding.chpTimestamp.isChecked,
                isUsingApiKey
            )
        }

        binding.srlLogging.setOnRefreshListener {
            if (!binding.chpAutoRefresh.isChecked) {
                getLogFromRetro(baseUrl,
                    contId,
                    token,
                    endpointId,
                    globalVars.logSettings?.linesCount ?: 1000,
                    binding.chpTimestamp.isChecked,
                    isUsingApiKey)
            }
        }

        binding.chpAutoRefresh.setOnClickListener {
            globActivity.setGlobalLoggingSettings(binding.chpAutoRefresh.isChecked,
                binding.chpTimestamp.isChecked,
                null,
                null
            )

            if (binding.chpAutoRefresh.isChecked) {
                binding.srlLogging.isEnabled = false
                timer.startTimer(this, baseUrl, endpointId, contId, token, globalVars, isUsingApiKey, binding.chpTimestamp.isChecked)
            } else {
                binding.srlLogging.isEnabled = true
                timer.cancelTimer()
            }
        }

        binding.chpAutoRefresh.setOnLongClickListener {
            // disable the auto refresh so next time when you turn it on - it will pull the correct auto refresh interval
            binding.chpAutoRefresh.isChecked = false
            binding.srlLogging.isEnabled = true
            timer.cancelTimer()

            val currentAutoRefreshInt: Long = globalVars.logSettings?.autoRefreshInterval ?: 6000L
            val nextArInterval =
                getNextListItem(currentAutoRefreshInt.toInt(), autoRefreshIntervals)
            globActivity.setGlobalLoggingSettings(binding.chpAutoRefresh.isChecked,
                binding.chpTimestamp.isChecked,
                null,
                nextArInterval.toLong()
            )

            globActivity.showGenericSnack(requireContext(),
                requireView(),
                "Auto refresh interval: ${nextArInterval / 1000} seconds",
                R.color.blue_main,
                R.color.dis2)

            true
        }

        binding.chpTimestamp.setOnClickListener {
            globActivity.setGlobalLoggingSettings(binding.chpAutoRefresh.isChecked,
                binding.chpTimestamp.isChecked,
                null,
                null
            )
        }

        binding.chpLinesCount.setOnClickListener {
            val currentLinesCount = globalVars.logSettings?.linesCount ?: 1000
            val nextLineCount = getNextListItem(currentLinesCount, linesOfLog)
            globActivity.setGlobalLoggingSettings(binding.chpAutoRefresh.isChecked,
                binding.chpTimestamp.isChecked,
                nextLineCount,
                null
            )
            binding.chpLinesCount.text = "${nextLineCount} lines"
        }

        binding.tbContainerLogging.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.chpLinesCount.setOnLongClickListener {
            val index = (eEgg.indices).random()

            globActivity.showGenericSnack(requireContext(),
                requireView(),
                eEgg[index],
                R.color.disText3,
                R.color.dis2)
            true
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, true) {
            if (!binding.srlLogging.isRefreshing) {
                timer.cancelTimer()
                findNavController().popBackStack()
            } else
                globActivity.showGenericSnack(requireContext(),
                    requireView(),
                    "Please wait until the logging request is finished..",
                    R.color.dis4,
                    R.color.blue_main)
        }
    }

    fun getLogFromRetro(
        baseUrl: String,
        containerId: String,
        jwt: String,
        endpointId: Int,
        numOfRows: Int,
        useTimestamp: Boolean,
        isUsingApiKey: Boolean,
    ) {
        fun readFromStream(
            body: ResponseBody?,
        ) {
            if (body != null) {
                val regexANSI = "\u001B\\[[;\\d]*m".toRegex()
                val regNoPrintable = "\\p{C}".toRegex()

                val charReader: Reader = body.charStream()

                val lines: List<String> = charReader.readLines().map { x ->
                    val noANSI = regexANSI.replace(x, "")
                    val noPrintable = regNoPrintable.replace(noANSI, "")

                    val fixedLine = noPrintable.drop(1)

                    val timeFormatted = if (binding.chpTimestamp.isChecked) {
                        val instant: Instant? = try {
                            Instant.parse(fixedLine.take(30))
                        } catch (ex: Exception) {
                            null
                        }

                        instant?.let {
                            val formattedTime = dtf.format(it)
                            "$formattedTime - ${fixedLine.drop(30)}"
                        } ?: fixedLine

                    } else
                        fixedLine
                    timeFormatted
                }

                val logItems: List<LogItem> = lines.map { line ->
                    LogItem(line)
                }

                charReader.close()

                binding.rvLogging.adapter = LoggingAdapter(logItems)
                binding.rvLogging.layoutManager = LinearLayoutManager(activity)
                binding.rvLogging.setHasFixedSize(true)

                binding.srlLogging.isRefreshing = false
                if (binding.chpAutoRefresh.isChecked)
                    binding.srlLogging.isEnabled = false

                binding.rvLogging.scrollToPosition(logItems.size - 1);

                toggleErrorTextView(
                    false,
                    null)
            } else {
                binding.srlLogging.isRefreshing = false
                toggleErrorTextView(
                    true,
                    "ERROR: Cannot read log data!")
            }
        }

        val fullPath =
            getString(R.string.getLog)
                .replace("{baseUrl}", baseUrl.removeSuffix("/"))
                .replace("{containerId}", containerId)
                .replace("{endpointId}", endpointId.toString())

        val (api, authType) = if (!isUsingApiKey) {
            Pair(RetrofitInstance.retrofitInstance!!.create(ApiInterface::class.java),
                "Bearer ${jwt}")
        } else {
            Pair(RetrofitInstance.retrofitInstance!!.create(ApiInterfaceApiKey::class.java), jwt)
        }

        api.getLog(fullPath, authType, 0, 1, 1, numOfRows, if (useTimestamp) 1 else 0)
            .enqueue(object : retrofit2.Callback<ResponseBody> {
                override fun onResponse(
                    call: Call<ResponseBody>,
                    response: Response<ResponseBody>,
                ) {
                    if (response.code() == 200) {
                        readFromStream(response.body())
                    } else {
                        binding.srlLogging.isRefreshing = false
                        toggleErrorTextView(
                            true,
                            "ERROR: Response code: ${response.code()}; ${
                                response.errorBody()?.string()
                            }")
                    }
                }

                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    binding.srlLogging.isRefreshing = false
                    toggleErrorTextView(
                        true,
                        "ERROR failure: ${t.message}")
                }
            })
    }

    private fun initChipsState(
        globalVars: GlobalApp,
    ) {
        globalVars.logSettings?.let {
            binding.chpAutoRefresh.isChecked = it.autoRefresh
            binding.chpTimestamp.isChecked = it.timestamp
            binding.chpLinesCount.text = "${it.linesCount} lines"
        }
    }

    private fun getNextListItem(selectedItem: Int, listOfItems: List<Int>): Int {
        val indexOfCurrent = listOfItems.indexOf(selectedItem)
        val indexOfNext = (indexOfCurrent + 1) % listOfItems.size
        return listOfItems[indexOfNext]
    }

    private fun toggleErrorTextView(
        show: Boolean,
        errorMsg: String?,
    ) {
        if (show) {
            errorMsg?.let {
                binding.tvLogError.text = it
            }
            binding.tvLogError.visibility = View.VISIBLE
            binding.rvLogging.visibility = View.GONE
        } else {
            binding.tvLogError.visibility = View.GONE
            binding.rvLogging.visibility = View.VISIBLE
        }
    }
}
package com.mironenko.internship_socket_chat.data.socket

import android.util.Log
import com.google.gson.Gson
import com.mironenko.internship_socket_chat.data.socket.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.*
import java.net.*
import javax.inject.Inject

const val SOCKET_TIMEOUT = 10000
const val PING_TIMEOUT = 9000L
const val DISCONNECT_TIMEOUT = 8000L
const val UPDATE_USERS_TIMEOUT = 1000L

class ChatSocketClient @Inject constructor(
) : ChatSocket {
    private val gsonObj = Gson()
    private val clientScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val usersScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var disconnectJob: Job? = null
    private var socketTCP: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null
    private var socketAddress: SocketAddress? = null
    private var isConnectedTcp = false

    private val _isAuthorized = MutableStateFlow(false)
    override val isAuthorized: Flow<Boolean> = _isAuthorized.asStateFlow()

    private val _users = MutableSharedFlow<List<User>>()
    override val users: Flow<List<User>> = _users.asSharedFlow()

    private var serverIp: String = ""
    private var userId: String = ""
    private var userLogin: String = ""

    override suspend fun connectToServerUdp() {
        var isSocketAddress = false
        withContext(Dispatchers.IO) {
            val socketUdp = DatagramSocket()
            socketUdp.broadcast = true
            socketUdp.soTimeout = SOCKET_TIMEOUT
            val buffer = ByteArray(256)

            val packetSend = DatagramPacket(
                buffer,
                buffer.size,
                InetAddress.getByName("255.255.255.255"),
                8888
            )
            val packetReceive = DatagramPacket(buffer, buffer.size)
            while (!isSocketAddress) {
                try {
                    socketUdp.send(packetSend)
                    socketUdp.receive(packetReceive)

                    socketAddress = packetReceive.socketAddress
                    isSocketAddress = packetReceive.data.isNotEmpty()
                    serverIp = splitSocketAddress(socketAddress.toString())
                } catch (e: SocketTimeoutException) {
                    e.printStackTrace()
                }
            }
        }
    }

    override suspend fun connectToServerTcp(login: String) {
        userLogin = login
        socketTCP = Socket(serverIp, 6666)
        socketTCP?.soTimeout = SOCKET_TIMEOUT
        writer = PrintWriter(OutputStreamWriter(socketTCP?.getOutputStream()))
        reader = BufferedReader(InputStreamReader(socketTCP?.getInputStream()))
        isConnectedTcp = true
        clientScope.launch {
            while (isConnectedTcp) {
                try {
                    val response = reader?.readLine() ?: continue
                    val baseDto = gsonObj.fromJson(response, BaseDto::class.java)
                    when (baseDto.action) {
                        BaseDto.Action.CONNECTED -> {
                            saveIdFromConnectedDto(baseDto = baseDto)
                            sendConnectDto()
                            authorizationStatus(true)
                            pingPongCycle()
                        }
                        BaseDto.Action.PONG -> {
                            cancelDisconnectJob()
                        }
                        BaseDto.Action.USERS_RECEIVED -> {
                            sharedUsers(baseDto = baseDto)
                            Log.d("TAG", baseDto.payload)
                        }
                        else -> {
                            throw IllegalArgumentException("Wrong BaseDto.Action")
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    override suspend fun updateUsersCycle() {
        usersScope.launch {
            while (isConnectedTcp) {
                Log.d("TAG", "UploadUsers start")
                sendJson(
                    jsonFromBaseDto(
                        action = BaseDto.Action.GET_USERS,
                        GetUsersDto(
                            id = userId
                        )
                    )
                )
                delay(UPDATE_USERS_TIMEOUT)
            }
        }
    }

    private suspend fun sharedUsers(baseDto: BaseDto) {
        val userList = gsonObj.fromJson(baseDto.payload, UsersReceivedDto::class.java)
        _users.emit(userList.users)
    }

    private fun saveIdFromConnectedDto(baseDto: BaseDto) {
        val connectedDto = gsonObj.fromJson(baseDto.payload, ConnectedDto::class.java)
        userId = connectedDto.id
    }

    private fun sendConnectDto() {
        sendJson(
            jsonFromBaseDto(
                action = BaseDto.Action.CONNECT,
                ConnectDto(
                    id = userId,
                    name = userLogin
                )
            )
        )
    }

    private fun pingPongCycle() {
        clientScope.launch {
            while (isConnectedTcp) {
                delay(PING_TIMEOUT)
                sendJson(
                    jsonFromBaseDto(
                        action = BaseDto.Action.PING,
                        PingDto(
                            id = userId
                        )
                    )
                )
                disconnectJob = createDisconnectJob()
            }
        }
    }

    private fun sendJson(baseDtoJson: String) {
        writer?.println(baseDtoJson)
        writer?.flush()
    }

    private fun jsonFromBaseDto(action: BaseDto.Action, payloadObj: Payload): String {
        val payload = gsonObj.toJson(payloadObj)
        val baseDto = BaseDto(action = action, payload = payload)
        return gsonObj.toJson(baseDto)
    }

    private fun splitSocketAddress(socketAddress: String): String {
        return socketAddress.substringAfter("/").substringBefore(":")
    }

    private fun authorizationStatus(status: Boolean) {
        _isAuthorized.value = status
    }

    private fun createDisconnectJob(): Job {
        return clientScope.launch {
            delay(DISCONNECT_TIMEOUT)
            authorizationStatus(false)
            clientScope.coroutineContext.cancelChildren()
            usersScope.coroutineContext.cancelChildren()
            socketClose()
        }
    }

    private fun cancelDisconnectJob() {
        disconnectJob?.cancel()
        disconnectJob = null
    }

    private fun socketClose() {
        isConnectedTcp = false
        socketTCP?.close()
        socketTCP = null
    }
}
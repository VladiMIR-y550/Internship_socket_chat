package com.mironenko.internship_socket_chat.ui.chat

import com.mironenko.internship_socket_chat.base.Reducer

class UserChatReducer(
    userId: String,
    receiverId: String
) : Reducer<UserChatState, UserChatAction> {

    override val initialState = UserChatState(
        userId = userId,
        receiverId = receiverId
    )

    override fun reduce(state: UserChatState, action: UserChatAction): UserChatState {
        return when (action) {
            UserChatAction.None -> state.copy(
                isLoading = false
            )

            is UserChatAction.ShowReceivedMessage -> {
                state.copy(
                    showMessage = action.chatMessage
                )
            }

            is UserChatAction.ShowSentMessage -> state.copy(
                showMessage = action.chatMessage
            )

            is UserChatAction.SetMessage -> state.copy(
                sendMessage = action.message,
                showMessage = null
            )
            is UserChatAction.SendMessage -> state.copy(
                isLoading = true,
                showMessage = null
            )
            is UserChatAction.NewMessageReceived -> state.copy(
                isLoading = true,
                showMessage = null
            )
            is UserChatAction.Error -> state.copy(
                errorMessage = action.error.toString()
            )
        }
    }
}
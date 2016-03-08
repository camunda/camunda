package net.long_running.transport;

@FunctionalInterface
public interface ChannelErrorHandler
{
    /**
     * An I/O error occured while putting the message on the wire.
     * This usually means that the channel was forcibly closed by the other side.
     * The sender should consider the message to be failed.
     */
    int SEND_ERROR = 1;

    /**
     * The message could not be sent because the connection was closed between the
     * moment the message was put into the send buffer and the connection was closed.
     * The sender should conider the messages to be failed.
     */
    int CHANNEL_CLOSED = 2;

    void onChannelError(int errorType, long messageId);

    ChannelErrorHandler DEFAULT_ERROR_HANDLER = (errorType, messageId) ->
    {
        System.out.println("Channel error of type "+ errorType + " on message id "+messageId);
    };

}

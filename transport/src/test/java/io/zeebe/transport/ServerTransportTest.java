package io.zeebe.transport;

import static io.zeebe.test.util.BufferAssert.assertThatBuffer;
import static org.assertj.core.api.Assertions.assertThat;

import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.test.util.TestUtil;
import io.zeebe.transport.util.RecordingMessageHandler;
import io.zeebe.util.actor.ActorScheduler;
import io.zeebe.util.actor.ActorSchedulerBuilder;
import io.zeebe.util.buffer.BufferUtil;

public class ServerTransportTest
{

    public static final DirectBuffer BUF1 = BufferUtil.wrapBytes(1, 2, 3, 4);
    public static final DirectBuffer BUF2 = BufferUtil.wrapBytes(5, 6, 7, 8);
    public static final SocketAddress SERVER_ADDRESS = new SocketAddress("localhost", 51115);

    public static final int REQUEST_POOL_SIZE = 4;
    public static final int SEND_BUFFER_SIZE = 16 * 1024;

    @Rule
    public AutoCloseableRule closeables = new AutoCloseableRule();

    protected ClientTransport clientTransport;
    protected ServerTransport serverTransport;

    private ActorScheduler actorScheduler;

    protected RecordingMessageHandler serverHandler = new RecordingMessageHandler();
    protected RecordingMessageHandler clientHandler = new RecordingMessageHandler();

    protected ClientInputMessageSubscription clientSubscription;

    @Before
    public void setUp()
    {
        actorScheduler = ActorSchedulerBuilder.createDefaultScheduler("test");

        final Dispatcher clientSendBuffer = Dispatchers.create("clientSendBuffer")
            .bufferSize(SEND_BUFFER_SIZE)
            .subscriptions("sender")
            .actorScheduler(actorScheduler)
            .build();
        closeables.manage(clientSendBuffer);

        final Dispatcher clientReceiveBuffer = Dispatchers.create("clientReceiveBuffer")
            .bufferSize(SEND_BUFFER_SIZE)
            .subscriptions("sender")
            .actorScheduler(actorScheduler)
            .build();
        closeables.manage(clientReceiveBuffer);

        clientTransport = Transports.newClientTransport()
            .sendBuffer(clientSendBuffer)
            .messageReceiveBuffer(clientReceiveBuffer)
            .requestPoolSize(REQUEST_POOL_SIZE)
            .scheduler(actorScheduler)
            .build();
        closeables.manage(clientTransport);

        final Dispatcher serverSendBuffer = Dispatchers.create("serverSendBuffer")
            .bufferSize(SEND_BUFFER_SIZE)
            .subscriptions("sender")
            .actorScheduler(actorScheduler)
            .build();
        closeables.manage(serverSendBuffer);

        serverTransport = Transports.newServerTransport()
            .sendBuffer(serverSendBuffer)
            .scheduler(actorScheduler)
            .bindAddress(SERVER_ADDRESS.toInetSocketAddress())
            .build(serverHandler, null);
        closeables.manage(serverTransport);

        clientSubscription = clientTransport.openSubscription("receiver", clientHandler).join();
    }

    /**
     * When a single remote reconnects, the stream ID for messages/responses sent by the server
     * should be changed, so that the client does not accidentally receive any messages
     * that were scheduled before reconnect and were in the send buffer during reconnection (for example
     * this is important for topic subscriptions).
     */
    @Test
    public void shouldNotSendMessagesForPreviousStreamIdAfterReconnect()
    {
        // given
        final TransportMessage message = new TransportMessage();

        final ServerOutput serverOutput = serverTransport.getOutput();
        final ClientOutput clientOutput = clientTransport.getOutput();

        final RemoteAddress remoteAddress = clientTransport.registerRemoteAddress(SERVER_ADDRESS);

        // connect client by making a request
        message.buffer(BUF1).remoteAddress(remoteAddress);
        clientOutput.sendMessage(message);
        TestUtil.waitUntil(() -> serverHandler.numReceivedMessages() == 1);

        final RemoteAddress firstRemote = serverHandler.getMessage(0).getRemote();

        // close client channel
        clientTransport.closeAllChannels().join();

        // reconnect by making a new request
        clientOutput.sendMessage(message);
        TestUtil.waitUntil(() -> serverHandler.numReceivedMessages() == 2);
        final RemoteAddress secondRemote = serverHandler.getMessage(1).getRemote();

        // when
        // sending server message with previous stream id
        message.buffer(BUF1).remoteStreamId(firstRemote.getStreamId());
        serverOutput.sendMessage(message);

        // and sending server message with new stream id
        message.buffer(BUF2).remoteStreamId(secondRemote.getStreamId());
        serverOutput.sendMessage(message);

        // then
        // first message has not been received by client
        assertThat(firstRemote.getStreamId()).isNotEqualTo(secondRemote.getStreamId());

        TestUtil.doRepeatedly(() -> clientSubscription.poll())
            .until(i -> clientHandler.numReceivedMessages() == 1);
        assertThatBuffer(clientHandler.getMessage(0).getBuffer()).hasBytes(BUF2);
    }
}

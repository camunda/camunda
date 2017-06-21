package org.camunda.tngp.transport;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.tngp.transport.impl.agent.Receiver;
import org.camunda.tngp.transport.util.RecordingChannelListener;
import org.camunda.tngp.util.actor.ActorScheduler;
import org.camunda.tngp.util.actor.ActorSchedulerImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TransportChannelListenerTest
{

    protected Transport clientTransport;
    protected Transport serverTransport;
    protected Receiver serverReceiver;
    private ActorScheduler actorScheduler;

    @Before
    public void setUp()
    {
        actorScheduler = ActorSchedulerImpl.createDefaultScheduler();

        clientTransport = Transports.createTransport("client")
            .actorScheduler(actorScheduler)
            .build();

        final TransportBuilder serverTransportBuilder = Transports.createTransport("server")
            .actorScheduler(actorScheduler);

        serverTransport = serverTransportBuilder.build();
        serverReceiver = serverTransportBuilder.getReceiver();
    }

    @After
    public void tearDown()
    {
        clientTransport.close();
        serverTransport.close();
        actorScheduler.close();
    }

    @Test
    public void shouldInvokeRegisteredListenerOnChannelClose() throws InterruptedException
    {
        // given
        final RecordingChannelListener clientListener = new RecordingChannelListener();
        clientTransport.registerChannelListener(clientListener);

        final RecordingChannelListener serverListener = new RecordingChannelListener();
        serverTransport.registerChannelListener(serverListener);

        final SocketAddress addr = new SocketAddress("localhost", 51115);

        serverTransport.createServerSocketBinding(addr).bind();
        final ChannelManager channelManager = clientTransport
                .createClientChannelPool()
                .build();

        final Channel channel = channelManager.requestChannel(addr);

        // when
        channelManager.closeAllChannelsAsync().join();

        // then
        assertThat(clientListener.getClosedChannels()).hasSize(1);
        assertThat(clientListener.getClosedChannels().get(0)).isSameAs(channel);

        // TODO: cannot reliably wait for channel being closed on server-side, since
        //   channel close notification happens asynchronously after client channel close
//        assertThat(serverListener.closedChannels).hasSize(1);

    }

    @Test
    public void shouldDeregisterListener()
    {
        // given
        final RecordingChannelListener clientListener = new RecordingChannelListener();
        clientTransport.registerChannelListener(clientListener);

        final RecordingChannelListener serverListener = new RecordingChannelListener();
        serverTransport.registerChannelListener(serverListener);

        final SocketAddress addr = new SocketAddress("localhost", 51115);

        serverTransport.createServerSocketBinding(addr).bind();
        final ChannelManager channelManager = clientTransport
                .createClientChannelPool()
                .build();
        channelManager.requestChannel(addr);

        // when
        clientTransport.removeChannelListener(clientListener);
        serverTransport.removeChannelListener(serverListener);

        // then
        channelManager.closeAllChannelsAsync().join();

        assertThat(clientListener.getClosedChannels()).hasSize(0);
        assertThat(serverListener.getClosedChannels()).hasSize(0);
    }
}

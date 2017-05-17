package org.camunda.tngp.transport;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.camunda.tngp.transport.TransportBuilder.ThreadingMode;
import org.camunda.tngp.transport.impl.agent.Receiver;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TransportChannelListenerTest
{

    protected Transport clientTransport;
    protected Transport serverTransport;
    protected Receiver serverReceiver;

    @Before
    public void setUp()
    {
        clientTransport = Transports.createTransport("client")
            .threadingMode(ThreadingMode.SHARED)
            .build();

        final TransportBuilder serverTransportBuilder = Transports.createTransport("server")
            .threadingMode(ThreadingMode.SHARED);

        serverTransport = serverTransportBuilder.build();
        serverReceiver = serverTransportBuilder.getReceiver();
    }

    @After
    public void tearDown()
    {
        clientTransport.close();
        serverTransport.close();
    }

    @Test
    public void shouldInvokeRegisteredListenerOnChannelClose() throws InterruptedException
    {
        // given
        final LoggingTransportChannelListener clientListener = new LoggingTransportChannelListener();
        clientTransport.registerChannelListener(clientListener);

        final LoggingTransportChannelListener serverListener = new LoggingTransportChannelListener();
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
        assertThat(clientListener.closedChannels).hasSize(1);
        assertThat(clientListener.closedChannels.get(0)).isSameAs(channel);

        // TODO: cannot reliably wait for channel being closed on server-side, since
        //   channel close notification happens asynchronously after client channel close
//        assertThat(serverListener.closedChannels).hasSize(1);

    }

    @Test
    public void shouldDeregisterListener()
    {
        // given
        final LoggingTransportChannelListener clientListener = new LoggingTransportChannelListener();
        clientTransport.registerChannelListener(clientListener);

        final LoggingTransportChannelListener serverListener = new LoggingTransportChannelListener();
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

        assertThat(clientListener.closedChannels).hasSize(0);
        assertThat(serverListener.closedChannels).hasSize(0);
    }

    public static class LoggingTransportChannelListener implements TransportChannelListener
    {

        protected List<Channel> closedChannels = new ArrayList<>();

        @Override
        public void onChannelClosed(Channel channel)
        {
            closedChannels.add(channel);
        }

    }
}

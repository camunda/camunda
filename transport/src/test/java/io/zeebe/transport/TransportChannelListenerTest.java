/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.transport;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static io.zeebe.util.buffer.DirectBufferWriter.writerFor;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.UnstableTest;
import io.zeebe.dispatcher.FragmentHandler;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.test.util.TestUtil;
import io.zeebe.transport.impl.DefaultChannelFactory;
import io.zeebe.transport.impl.RemoteAddressImpl;
import io.zeebe.transport.impl.TransportChannel;
import io.zeebe.transport.impl.TransportChannel.ChannelLifecycleListener;
import io.zeebe.transport.impl.TransportChannel.TransportChannelMetrics;
import io.zeebe.transport.impl.TransportChannelFactory;
import io.zeebe.transport.impl.util.SocketUtil;
import io.zeebe.transport.util.RecordingChannelListener;
import io.zeebe.transport.util.RecordingChannelListener.Event;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.metrics.MetricsManager;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.RuleChain;

public class TransportChannelListenerTest {
  public ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule(3);
  public AutoCloseableRule closeables = new AutoCloseableRule();

  @Rule public RuleChain ruleChain = RuleChain.outerRule(actorSchedulerRule).around(closeables);

  private static final int NODE_ID = 1;
  private static final SocketAddress ADDRESS = SocketUtil.getNextAddress();

  protected static final DirectBuffer EMPTY_BUFFER = new UnsafeBuffer(0, 0);
  protected static final BufferWriter WRITER = writerFor(EMPTY_BUFFER);

  protected ServerTransport serverTransport;

  @Before
  public void setUp() {
    serverTransport =
        Transports.newServerTransport()
            .bindAddress(ADDRESS.toInetSocketAddress())
            .scheduler(actorSchedulerRule.get())
            .build(null, null);
    closeables.manage(serverTransport);
  }

  private ClientTransport buildClientTransport() {
    return buildClientTransport(b -> {});
  }

  private ClientTransport buildClientTransport(Consumer<ClientTransportBuilder> builderConsumer) {
    final ClientTransportBuilder transportBuilder =
        Transports.newClientTransport("test").scheduler(actorSchedulerRule.get());
    builderConsumer.accept(transportBuilder);

    final ClientTransport clientTransport = transportBuilder.build();
    closeables.manage(clientTransport);

    return clientTransport;
  }

  @Test
  public void shouldInvokeRegisteredListenerOnChannelClose() {
    // given
    final ClientTransport clientTransport = buildClientTransport();

    final RecordingChannelListener clientListener = new RecordingChannelListener();
    clientTransport.registerChannelListener(clientListener);

    final RecordingChannelListener serverListener = new RecordingChannelListener();
    serverTransport.registerChannelListener(serverListener);

    clientTransport.registerEndpoint(NODE_ID, ADDRESS);

    // opens a channel asynchronously
    clientTransport.getOutput().sendRequest(NODE_ID, WRITER);

    TestUtil.waitUntil(() -> !clientListener.getOpenedConnections().isEmpty());

    // when
    clientTransport.closeAllChannels().join();

    // then
    TestUtil.waitUntil(() -> !clientListener.getClosedConnections().isEmpty());
    assertThat(clientListener.getClosedConnections()).hasSize(1);
    assertThat(clientListener.getClosedConnections().get(0))
        .extracting("address")
        .containsExactly(ADDRESS);

    TestUtil.waitUntil(() -> !serverListener.getClosedConnections().isEmpty());
    assertThat(serverListener.getClosedConnections()).hasSize(1);
  }

  @Test
  @Category(UnstableTest.class)
  public void shouldInvokeRegisteredListenerOnChannelOpened() {
    // given
    final ClientTransport clientTransport = buildClientTransport();

    final RecordingChannelListener clientListener = new RecordingChannelListener();
    clientTransport.registerChannelListener(clientListener);

    final RecordingChannelListener serverListener = new RecordingChannelListener();
    serverTransport.registerChannelListener(serverListener);

    clientTransport.registerEndpoint(NODE_ID, ADDRESS);

    // when
    clientTransport.getOutput().sendRequest(NODE_ID, WRITER);

    // then
    TestUtil.waitUntil(() -> !clientListener.getOpenedConnections().isEmpty());
    TestUtil.waitUntil(() -> !serverListener.getOpenedConnections().isEmpty());

    assertThat(clientListener.getOpenedConnections())
        .extracting("address")
        .containsExactly(ADDRESS);
    assertThat(serverListener.getOpenedConnections()).hasSize(1);
  }

  @Test
  public void shouldDeregisterListener() {
    // given
    final ClientTransport clientTransport = buildClientTransport();

    final RecordingChannelListener clientListener = new RecordingChannelListener();
    clientTransport.registerChannelListener(clientListener);

    final RecordingChannelListener serverListener = new RecordingChannelListener();
    serverTransport.registerChannelListener(serverListener);

    clientTransport.registerEndpoint(NODE_ID, ADDRESS);

    clientTransport.getOutput().sendRequest(NODE_ID, WRITER);
    TestUtil.waitUntil(() -> !clientListener.getOpenedConnections().isEmpty());

    clientTransport.removeChannelListener(clientListener);
    serverTransport.removeChannelListener(serverListener);

    // when
    clientTransport.closeAllChannels().join();

    // then

    assertThat(clientListener.getClosedConnections()).hasSize(0);
    assertThat(serverListener.getClosedConnections()).hasSize(0);
  }

  @Test
  public void shouldNotInvokeListenerWhenChannelCannotConnect() {
    // given
    final CountingChannelFactory clientChannelFactory = new CountingChannelFactory();
    final ClientTransport clientTransport =
        buildClientTransport(b -> b.channelFactory(clientChannelFactory));

    final RecordingChannelListener clientListener = new RecordingChannelListener();
    clientTransport.registerChannelListener(clientListener);

    serverTransport.close();

    // when
    clientTransport.registerEndpoint(NODE_ID, ADDRESS); // triggering connection attempts

    // then
    waitUntil(
        () -> clientChannelFactory.getCreatedChannels() >= 2); // first connection attempt failed
    assertThat(clientListener.getOpenedConnections()).isEmpty();
    assertThat(clientListener.getClosedConnections()).isEmpty();
  }

  @Test
  public void shouldInvokeChannelListenersInCorrectOrderWhenChannelClosesImmediately() {
    // given
    final ImmediatelyClosingChannelFactory clientChannelFactory =
        new ImmediatelyClosingChannelFactory();
    final ClientTransport clientTransport =
        buildClientTransport(b -> b.channelFactory(clientChannelFactory));

    final RecordingChannelListener clientListener = new RecordingChannelListener();
    clientTransport.registerChannelListener(clientListener);

    // when
    clientTransport.registerEndpoint(NODE_ID, ADDRESS); // triggering connection attempts
    waitUntil(
        () ->
            clientListener.getEvents().size()
                >= 2); // first cycle of opening and closing the channel

    // then
    assertThat(clientListener.getEvents()).startsWith(Event.ESTABLISHED, Event.CLOSED);
  }

  protected static class ImmediatelyClosingChannelFactory implements TransportChannelFactory {
    private final TransportChannelMetrics metrics =
        new TransportChannelMetrics(new MetricsManager(), "test");

    @Override
    public TransportChannel buildClientChannel(
        ChannelLifecycleListener listener,
        RemoteAddressImpl remoteAddress,
        int maxMessageSize,
        FragmentHandler readHandler) {
      return new TransportChannel(listener, remoteAddress, maxMessageSize, readHandler, metrics) {
        @Override
        public void finishConnect() {
          super.finishConnect();
          doClose();
        }
      };
    }

    @Override
    public TransportChannel buildServerChannel(
        ChannelLifecycleListener listener,
        RemoteAddressImpl remoteAddress,
        int maxMessageSize,
        FragmentHandler readHandler,
        SocketChannel media) {
      throw new UnsupportedOperationException();
    }
  }

  protected static class CountingChannelFactory implements TransportChannelFactory {
    protected AtomicInteger createdChannels = new AtomicInteger();
    protected TransportChannelFactory actualFactory = new DefaultChannelFactory();

    @Override
    public TransportChannel buildClientChannel(
        ChannelLifecycleListener listener,
        RemoteAddressImpl remoteAddress,
        int maxMessageSize,
        FragmentHandler readHandler) {
      createdChannels.incrementAndGet();
      return actualFactory.buildClientChannel(listener, remoteAddress, maxMessageSize, readHandler);
    }

    public int getCreatedChannels() {
      return createdChannels.get();
    }

    @Override
    public TransportChannel buildServerChannel(
        ChannelLifecycleListener listener,
        RemoteAddressImpl remoteAddress,
        int maxMessageSize,
        FragmentHandler readHandler,
        SocketChannel media) {
      throw new UnsupportedOperationException();
    }
  }
}

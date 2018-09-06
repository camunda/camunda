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

import static io.zeebe.test.util.BufferAssert.assertThatBuffer;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static io.zeebe.util.buffer.DirectBufferWriter.writerFor;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.test.util.TestUtil;
import io.zeebe.transport.impl.util.SocketUtil;
import io.zeebe.transport.util.RecordingChannelListener;
import io.zeebe.transport.util.RecordingMessageHandler;
import io.zeebe.util.ByteValue;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class ServerTransportTest {
  public static final DirectBuffer BUF1 = BufferUtil.wrapBytes(1, 2, 3, 4);
  public static final DirectBuffer BUF2 = BufferUtil.wrapBytes(5, 6, 7, 8);

  public static final int NODE_ID = 1;
  public static final SocketAddress SERVER_ADDRESS = SocketUtil.getNextAddress();

  public static final ByteValue SEND_BUFFER_SIZE = ByteValue.ofKilobytes(16);

  public ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule(3);
  public AutoCloseableRule closeables = new AutoCloseableRule();

  @Rule public RuleChain ruleChain = RuleChain.outerRule(actorSchedulerRule).around(closeables);

  protected ClientTransport clientTransport;
  protected ServerTransport serverTransport;

  protected RecordingMessageHandler serverHandler = new RecordingMessageHandler();
  protected RecordingMessageHandler clientHandler = new RecordingMessageHandler();

  protected ClientInputMessageSubscription clientSubscription;

  @Before
  public void setUp() {
    final Dispatcher clientReceiveBuffer =
        Dispatchers.create("clientReceiveBuffer")
            .bufferSize(SEND_BUFFER_SIZE)
            .actorScheduler(actorSchedulerRule.get())
            .build();
    closeables.manage(clientReceiveBuffer);

    clientTransport =
        Transports.newClientTransport("test")
            .messageReceiveBuffer(clientReceiveBuffer)
            .scheduler(actorSchedulerRule.get())
            .build();
    closeables.manage(clientTransport);

    serverTransport =
        Transports.newServerTransport()
            .scheduler(actorSchedulerRule.get())
            .bindAddress(SERVER_ADDRESS.toInetSocketAddress())
            .build(serverHandler, null);
    closeables.manage(serverTransport);

    clientSubscription = clientTransport.openSubscription("receiver", clientHandler).join();
  }

  /**
   * When a single remote reconnects, the stream ID for messages/responses sent by the server should
   * be changed, so that the client does not accidentally receive any messages that were scheduled
   * before reconnect and were in the send buffer during reconnection (for example this is important
   * for subscriptions).
   */
  @Test
  public void shouldNotSendMessagesForPreviousStreamIdAfterReconnect() {
    // given
    final ServerOutput serverOutput = serverTransport.getOutput();
    final ClientOutput clientOutput = clientTransport.getOutput();

    final RecordingChannelListener channelListener = new RecordingChannelListener();

    clientTransport.registerChannelListener(channelListener);

    clientTransport.registerEndpoint(NODE_ID, SERVER_ADDRESS);
    waitUntil(() -> channelListener.getOpenedConnections().size() == 1);

    // make first request
    clientOutput.sendMessage(NODE_ID, writerFor(BUF1));
    TestUtil.waitUntil(() -> serverHandler.numReceivedMessages() == 1);

    final RemoteAddress firstRemote = serverHandler.getMessage(0).getRemote();

    // close client channel
    clientTransport.closeAllChannels().join();

    // wait for reconnect
    waitUntil(() -> channelListener.getOpenedConnections().size() == 2);

    // make a second request
    clientOutput.sendMessage(NODE_ID, writerFor(BUF1));
    TestUtil.waitUntil(() -> serverHandler.numReceivedMessages() == 2);
    final RemoteAddress secondRemote = serverHandler.getMessage(1).getRemote();

    // when
    // sending server message with previous stream id
    serverOutput.sendMessage(firstRemote.getStreamId(), writerFor(BUF1));

    // and sending server message with new stream id
    serverOutput.sendMessage(secondRemote.getStreamId(), writerFor(BUF2));

    // then
    // first message has not been received by client
    assertThat(firstRemote.getStreamId()).isNotEqualTo(secondRemote.getStreamId());

    TestUtil.doRepeatedly(() -> clientSubscription.poll())
        .until(i -> clientHandler.numReceivedMessages() == 1);
    assertThatBuffer(clientHandler.getMessage(0).getBuffer()).hasBytes(BUF2);
  }
}

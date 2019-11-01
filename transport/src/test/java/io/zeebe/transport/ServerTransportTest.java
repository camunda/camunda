/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.test.util.socket.SocketUtil;
import io.zeebe.transport.util.RecordingMessageHandler;
import io.zeebe.util.ByteValue;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.RuleChain;

public class ServerTransportTest {
  public static final DirectBuffer BUF1 = BufferUtil.wrapBytes(1, 2, 3, 4);
  public static final DirectBuffer BUF2 = BufferUtil.wrapBytes(5, 6, 7, 8);

  public static final int NODE_ID = 1;
  public static final SocketAddress SERVER_ADDRESS = new SocketAddress(SocketUtil.getNextAddress());

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
}

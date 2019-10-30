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
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.RuleChain;

public class BufferingServerTransportTest {
  public static final ByteValue BUFFER_SIZE = ByteValue.ofKilobytes(16);
  public static final SocketAddress SERVER_ADDRESS = new SocketAddress(SocketUtil.getNextAddress());

  public ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule(3);
  public AutoCloseableRule closeables = new AutoCloseableRule();

  @Rule public RuleChain ruleChain = RuleChain.outerRule(actorSchedulerRule).around(closeables);

  protected ClientTransport clientTransport;
  protected BufferingServerTransport serverTransport;

  protected RecordingMessageHandler serverHandler = new RecordingMessageHandler();
  private Dispatcher serverReceiveBuffer;

  @Before
  public void setUp() {
    clientTransport =
        Transports.newClientTransport("test").scheduler(actorSchedulerRule.get()).build();
    closeables.manage(clientTransport);

    serverReceiveBuffer =
        Dispatchers.create("serverReceiveBuffer")
            .bufferSize(BUFFER_SIZE)
            .actorScheduler(actorSchedulerRule.get())
            .build();
    closeables.manage(serverReceiveBuffer);

    serverTransport =
        Transports.newServerTransport()
            .scheduler(actorSchedulerRule.get())
            .bindAddress(SERVER_ADDRESS.toInetSocketAddress())
            .buildBuffering(serverReceiveBuffer);
    closeables.manage(serverTransport);
  }
}

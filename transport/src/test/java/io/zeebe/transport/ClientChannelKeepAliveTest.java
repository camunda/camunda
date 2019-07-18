/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.test.util.TestUtil;
import io.zeebe.transport.impl.ControlMessages;
import io.zeebe.transport.impl.util.SocketUtil;
import io.zeebe.util.sched.clock.ActorClock;
import io.zeebe.util.sched.clock.ControlledActorClock;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class ClientChannelKeepAliveTest {
  protected static final Duration KEEP_ALIVE_PERIOD = Duration.ofSeconds(1);
  protected static final int NODE_ID = 1;
  protected static final SocketAddress ADDRESS = SocketUtil.getNextAddress();
  protected static final int NODE_ID2 = 2;
  protected static final SocketAddress ADDRESS2 = SocketUtil.getNextAddress();
  public AutoCloseableRule closeables = new AutoCloseableRule();
  public ControlledActorClock clock = new ControlledActorClock();
  public ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule(3, clock);
  @Rule public RuleChain ruleChain = RuleChain.outerRule(actorSchedulerRule).around(closeables);
  protected ControlMessageRecorder serverRecorder;

  @Before
  public void setUp() {
    serverRecorder = new ControlMessageRecorder();
    buildServerTransport(ADDRESS, serverRecorder);
  }

  protected ServerTransport buildServerTransport(
      SocketAddress bindAddress, ControlMessageRecorder recorder) {
    final ServerTransport serverTransport =
        Transports.newServerTransport()
            .bindAddress(bindAddress.toInetSocketAddress())
            .scheduler(actorSchedulerRule.get())
            .controlMessageListener(recorder)
            .build(null, null);
    closeables.manage(serverTransport);

    return serverTransport;
  }

  protected ClientTransport buildClientTransport(Duration keepAlivePeriod) {
    final ClientTransportBuilder transportBuilder =
        Transports.newClientTransport("test").scheduler(actorSchedulerRule.get());

    if (keepAlivePeriod != null) {
      transportBuilder.keepAlivePeriod(keepAlivePeriod);
    }

    final ClientTransport clientTransport = transportBuilder.build();
    closeables.manage(clientTransport);

    return clientTransport;
  }

  protected void openChannel(ClientTransport transport, int nodeId, SocketAddress target) {
    transport.registerEndpointAndAwaitChannel(nodeId, target);
  }

  @After
  public void tearDown() {}

  @Test
  public void shouldExposeKeepAliveProperty() {
    // when
    final ClientTransport transport = buildClientTransport(KEEP_ALIVE_PERIOD);

    // then
    assertThat(transport.getChannelKeepAlivePeriod()).isEqualTo(KEEP_ALIVE_PERIOD);
  }

  @Test
  public void shouldSendFirstKeepAlive() {
    // given
    clock.setCurrentTime(1000);
    final ClientTransport transport = buildClientTransport(KEEP_ALIVE_PERIOD);

    openChannel(transport, NODE_ID, ADDRESS);

    // when
    clock.addTime(KEEP_ALIVE_PERIOD.plusMillis(1));

    // then
    TestUtil.waitUntil(() -> !serverRecorder.getReceivedFrames().isEmpty());
    assertThat(serverRecorder.getReceivedFrames()).hasSize(1);
    assertThat(serverRecorder.getReceivedFrames().get(0).type)
        .isEqualTo(ControlMessages.KEEP_ALIVE_TYPE);
    assertThat(serverRecorder.getReceivedFrames().get(0).timestamp)
        .isEqualTo(clock.getCurrentTimeInMillis());
  }

  @Test
  public void shouldSendSubsequentKeepAlives() {
    // given
    clock.setCurrentTime(Instant.now());
    final ClientTransport transport = buildClientTransport(KEEP_ALIVE_PERIOD);

    openChannel(transport, NODE_ID, ADDRESS);

    clock.addTime(KEEP_ALIVE_PERIOD.plusMillis(1));
    final long timestamp1 = clock.getCurrentTimeInMillis();

    TestUtil.waitUntil(() -> !serverRecorder.getReceivedFrames().isEmpty());

    // when
    clock.addTime(KEEP_ALIVE_PERIOD.plusMillis(1));
    final long timestamp2 = clock.getCurrentTimeInMillis();

    // then
    TestUtil.waitUntil(() -> serverRecorder.getReceivedFrames().size() == 2);
    assertThat(serverRecorder.getReceivedFrames()).hasSize(2);
    assertThat(serverRecorder.getReceivedFrames().get(0).type)
        .isEqualTo(ControlMessages.KEEP_ALIVE_TYPE);
    assertThat(serverRecorder.getReceivedFrames().get(0).timestamp).isEqualTo(timestamp1);
    assertThat(serverRecorder.getReceivedFrames().get(1).type)
        .isEqualTo(ControlMessages.KEEP_ALIVE_TYPE);
    assertThat(serverRecorder.getReceivedFrames().get(1).timestamp).isEqualTo(timestamp2);
  }

  @Test
  public void shouldUseDefaultKeepAlive() throws InterruptedException {
    // given
    clock.setCurrentTime(Instant.now());

    final int expectedDefaultKeepAlive = 5000;
    final ClientTransport transport = buildClientTransport(null);

    openChannel(transport, NODE_ID, ADDRESS);

    // when
    clock.addTime(Duration.ofMillis(expectedDefaultKeepAlive - 1));
    Thread.sleep(500L);

    assertThat(serverRecorder.getReceivedFrames()).isEmpty();

    // when
    clock.addTime(Duration.ofMillis(2));

    // then
    TestUtil.waitUntil(() -> !serverRecorder.getReceivedFrames().isEmpty());
    assertThat(serverRecorder.getReceivedFrames()).hasSize(1);
    assertThat(serverRecorder.getReceivedFrames().get(0).type)
        .isEqualTo(ControlMessages.KEEP_ALIVE_TYPE);
    assertThat(serverRecorder.getReceivedFrames().get(0).timestamp)
        .isEqualTo(clock.getCurrentTimeInMillis());
  }

  @Test
  public void shouldSendKeepAliveForMultipleChannels() {
    // given
    clock.setCurrentTime(Instant.now());
    final ControlMessageRecorder secondServerRecorder = new ControlMessageRecorder();

    buildServerTransport(ADDRESS2, secondServerRecorder);

    final ClientTransport clientTransport = buildClientTransport(KEEP_ALIVE_PERIOD);

    openChannel(clientTransport, NODE_ID, ADDRESS);
    openChannel(clientTransport, NODE_ID2, ADDRESS2);

    // when
    clock.addTime(KEEP_ALIVE_PERIOD.plusMillis(1));

    // then
    TestUtil.waitUntil(() -> !serverRecorder.getReceivedFrames().isEmpty());
    TestUtil.waitUntil(() -> !secondServerRecorder.getReceivedFrames().isEmpty());

    assertThat(serverRecorder.getReceivedFrames()).hasSize(1);
    assertThat(serverRecorder.getReceivedFrames().get(0).timestamp)
        .isEqualTo(clock.getCurrentTimeInMillis());

    assertThat(secondServerRecorder.getReceivedFrames()).hasSize(1);
    assertThat(secondServerRecorder.getReceivedFrames().get(0).timestamp)
        .isEqualTo(clock.getCurrentTimeInMillis());
  }

  @Test
  public void shouldNotSendKeepAliveWhenPeriodIsZero() throws Exception {
    // given
    clock.setCurrentTime(Instant.now());
    final ClientTransport clientTransport = buildClientTransport(null);

    openChannel(clientTransport, NODE_ID, ADDRESS);

    // when
    clock.setCurrentTime(Long.MAX_VALUE);
    Thread.sleep(1000L); // can't wait for sender to do nothing, so have to sleep for a bit

    // then
    assertThat(serverRecorder.getReceivedFrames()).isEmpty();
  }

  protected static class ControlMessageRecorder implements ServerControlMessageListener {
    protected List<ControlFrame> receivedFrames = new CopyOnWriteArrayList<>();

    @Override
    public void onMessage(
        ServerOutput output, RemoteAddress remoteAddress, int controlMessageType) {
      final ControlFrame frame = new ControlFrame();
      frame.type = controlMessageType;
      frame.timestamp = ActorClock.currentTimeMillis();

      receivedFrames.add(frame);
    }

    public List<ControlFrame> getReceivedFrames() {
      return receivedFrames;
    }
  }

  protected static class ControlFrame {
    int type;
    long timestamp;
  }
}

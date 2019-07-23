/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static io.zeebe.util.buffer.DirectBufferWriter.writerFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.transport.impl.memory.NonBlockingMemoryPool;
import io.zeebe.transport.impl.util.SocketUtil;
import io.zeebe.transport.util.EchoRequestResponseHandler;
import io.zeebe.transport.util.RecordingMessageHandler;
import io.zeebe.util.ByteValue;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.sched.clock.ControlledActorClock;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.time.Duration;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class ClientTransportMemoryTest {
  public static final DirectBuffer BUF1 = BufferUtil.wrapBytes(1, 2, 3, 4);
  public static final BufferWriter WRITER1 = writerFor(BUF1);
  public static final int NODE_ID1 = 1;
  public static final SocketAddress SERVER_ADDRESS1 = SocketUtil.getNextAddress();
  public AutoCloseableRule closeables = new AutoCloseableRule();
  public ControlledActorClock clock = new ControlledActorClock();
  public ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule(3, clock);
  @Rule public RuleChain ruleChain = RuleChain.outerRule(actorSchedulerRule).around(closeables);
  protected ClientTransport clientTransport;
  private NonBlockingMemoryPool requestMemoryPoolSpy;
  private NonBlockingMemoryPool messageMemoryPoolSpy;

  @Before
  public void setUp() {
    requestMemoryPoolSpy = spy(new NonBlockingMemoryPool(ByteValue.ofMegabytes(4)));
    messageMemoryPoolSpy = spy(new NonBlockingMemoryPool(ByteValue.ofMegabytes(4)));

    clientTransport =
        Transports.newClientTransport("test")
            .scheduler(actorSchedulerRule.get())
            .requestMemoryPool(requestMemoryPoolSpy)
            .messageMemoryPool(messageMemoryPoolSpy)
            .defaultMessageRetryTimeout(Duration.ofMillis(100))
            .build();
    closeables.manage(clientTransport);
  }

  protected ServerTransport buildServerTransport(
      Function<ServerTransportBuilder, ServerTransport> builderConsumer) {
    final Dispatcher serverSendBuffer =
        Dispatchers.create("serverSendBuffer")
            .bufferSize(ByteValue.ofMegabytes(4))
            .actorScheduler(actorSchedulerRule.get())
            .build();
    closeables.manage(serverSendBuffer);

    final ServerTransportBuilder transportBuilder =
        Transports.newServerTransport().scheduler(actorSchedulerRule.get());

    final ServerTransport serverTransport = builderConsumer.apply(transportBuilder);
    closeables.manage(serverTransport);

    return serverTransport;
  }

  @Test
  public void shouldReclaimRequestMemoryOnRequestTimeout() {
    // given
    registerEndpoint();

    final ClientOutput output = clientTransport.getOutput();

    // when
    final ActorFuture<ClientResponse> responseFuture =
        output.sendRequest(NODE_ID1, WRITER1, Duration.ofMillis(500));

    // then
    assertThatThrownBy(responseFuture::join).hasMessageContaining("Request timed out after PT0.5S");
    verify(requestMemoryPoolSpy, times(1)).allocate(anyInt());
    verify(requestMemoryPoolSpy, timeout(500).times(1))
        .reclaim(any()); // released after future is completed
  }

  @Test
  public void shouldReclaimRequestMemoryIfFutureCompleteFails() {
    // given
    registerEndpoint();
    final ClientOutput output = clientTransport.getOutput();
    final ActorFuture<ClientResponse> responseFuture =
        output.sendRequest(NODE_ID1, WRITER1, Duration.ofMillis(500));

    // when

    // future is completed before timeout occurs to simmulate situation in which future can not be
    // completed
    responseFuture.complete(null);

    // then
    verify(requestMemoryPoolSpy, times(1)).allocate(anyInt());
    verify(requestMemoryPoolSpy, timeout(5000).times(1)).reclaim(any()); // released of timeout
  }

  @Test
  public void shouldReclaimRequestMemoryOnResponse() {
    // given
    final BufferWriter writer = mock(BufferWriter.class);
    when(writer.getLength()).thenReturn(16);

    buildServerTransport(
        b ->
            b.bindAddress(SERVER_ADDRESS1.toInetSocketAddress())
                .build(null, new EchoRequestResponseHandler()));

    registerEndpoint();

    clientTransport.getOutput().sendRequest(NODE_ID1, writer).join();

    verify(requestMemoryPoolSpy, times(1)).allocate(anyInt());
    verify(requestMemoryPoolSpy, timeout(500).times(1))
        .reclaim(any()); // released after future is completed
  }

  @Test
  public void shouldReclaimRequestMemoryOnRequestWriterException() {
    // given
    final BufferWriter writer = mock(BufferWriter.class);
    when(writer.getLength()).thenReturn(16);
    doThrow(RuntimeException.class).when(writer).write(any(), anyInt());

    registerEndpoint();

    try {
      clientTransport.getOutput().sendRequest(NODE_ID1, writer);
    } catch (RuntimeException e) {
      // expected
    }

    verify(requestMemoryPoolSpy, times(1)).allocate(anyInt());
    verify(requestMemoryPoolSpy, times(1)).reclaim(any());
  }

  @Test
  public void shouldReclaimOnMessageSend() {
    // given
    final BufferWriter writer = mock(BufferWriter.class);
    when(writer.getLength()).thenReturn(16);

    final RecordingMessageHandler messageHandler = new RecordingMessageHandler();

    buildServerTransport(
        b -> b.bindAddress(SERVER_ADDRESS1.toInetSocketAddress()).build(messageHandler, null));

    clientTransport.registerEndpoint(NODE_ID1, SERVER_ADDRESS1);

    clientTransport.getOutput().sendMessage(NODE_ID1, writer);

    waitUntil(() -> messageHandler.numReceivedMessages() == 1);

    verify(messageMemoryPoolSpy, times(1)).allocate(anyInt());
    verify(messageMemoryPoolSpy, times(1)).reclaim(any());
  }

  @Test
  public void shouldReclaimOnMessageSendFailed() {
    // given
    final BufferWriter writer = mock(BufferWriter.class);
    when(writer.getLength()).thenReturn(16);

    // no channel open
    clientTransport.registerEndpoint(NODE_ID1, SERVER_ADDRESS1);

    // when
    clientTransport.getOutput().sendMessage(NODE_ID1, writer);

    // then
    verify(messageMemoryPoolSpy, times(1)).allocate(anyInt());
    verify(messageMemoryPoolSpy, timeout(1000).times(1)).reclaim(any());
  }

  @Test
  public void shouldReclaimOnMessageWriterException() {
    // given
    final BufferWriter writer = mock(BufferWriter.class);
    when(writer.getLength()).thenReturn(16);
    doThrow(RuntimeException.class).when(writer).write(any(), anyInt());

    clientTransport.registerEndpoint(NODE_ID1, SERVER_ADDRESS1);

    try {
      clientTransport.getOutput().sendMessage(NODE_ID1, writer);
      fail("expected exception");
    } catch (Exception e) {
      // expected
    }

    verify(messageMemoryPoolSpy, times(1)).allocate(anyInt());
    verify(messageMemoryPoolSpy, times(1)).reclaim(any());
  }

  @Test
  public void shouldRejectMessageWhenBufferPoolExhaused() {
    // given
    final ClientOutput output = clientTransport.getOutput();

    doReturn(null).when(messageMemoryPoolSpy).allocate(anyInt());

    // when
    final boolean success = output.sendMessage(NODE_ID1, WRITER1);

    // then
    assertThat(success).isFalse();
  }

  @Test
  public void shouldRejectRequestWhenBufferPoolExhaused() {
    // given
    final ClientOutput output = clientTransport.getOutput();
    registerEndpoint();

    doReturn(null).when(requestMemoryPoolSpy).allocate(anyInt());

    // when
    final ActorFuture<ClientResponse> reqFuture = output.sendRequest(NODE_ID1, WRITER1);

    // then
    assertThat(reqFuture).isNull();
  }

  private void registerEndpoint() {
    clientTransport.registerEndpoint(NODE_ID1, SERVER_ADDRESS1);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.stream;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.impl.stream.StreamJobsHandler.JobStreamConsumer;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivatedJob;
import io.camunda.zeebe.msgpack.spec.MsgpackReaderException;
import io.camunda.zeebe.protocol.impl.stream.job.ActivatedJobImpl;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.transport.stream.api.ClientStreamBlockedException;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.agrona.LangUtil;
import org.junit.jupiter.api.Test;

final class JobStreamConsumerTest {
  private final TestConcurrencyControl executor = new TestConcurrencyControl();

  @Test
  void shouldRethrowExceptionOnPushFailure() {
    // given
    final var failure = new RuntimeException("failed");
    final var clientObserver = new TestStreamObserver();
    final var consumer = new JobStreamConsumer(clientObserver, executor);
    clientObserver.failure = failure;

    // when
    final var result = consumer.push(BufferUtil.createCopy(new ActivatedJobImpl()));

    // then
    assertThat(result)
        .failsWithin(Duration.ZERO)
        .withThrowableThat()
        .havingRootCause()
        .isSameAs(failure);
    assertThat(clientObserver.error).isSameAs(failure);
  }

  @Test
  void shouldFailPushOnSerialization() {
    // given
    final var clientObserver = new TestStreamObserver();
    final var consumer = new JobStreamConsumer(clientObserver, executor);

    // when
    final var result = consumer.push(BufferUtil.wrapString("i am not a job"));

    // then
    assertThat(result)
        .failsWithin(Duration.ZERO)
        .withThrowableThat()
        .havingRootCause()
        .isInstanceOf(MsgpackReaderException.class);
    assertThat(clientObserver.error).as("client stream is not closed").isNull();
  }

  @Test
  void shouldFailPushOnClientStreamNotReady() {
    // given
    final var clientObserver = new TestStreamObserver();
    final var consumer = new JobStreamConsumer(clientObserver, executor);
    clientObserver.isReady = false;

    // when
    final var result = consumer.push(BufferUtil.createCopy(new ActivatedJobImpl()));

    // then
    assertThat(result)
        .failsWithin(Duration.ZERO)
        .withThrowableThat()
        .havingRootCause()
        .isInstanceOf(ClientStreamBlockedException.class);
    assertThat(clientObserver.error).as("client stream is not closed").isNull();
  }

  @Test
  void shouldPushPayload() {
    // given
    final var clientObserver = new TestStreamObserver();
    final var consumer = new JobStreamConsumer(clientObserver, executor);
    final var job = new ActivatedJobImpl().setJobKey(1);

    // when
    final var result = consumer.push(BufferUtil.createCopy(job));

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(5));
    assertThat(clientObserver.error).isNull();
    assertThat(clientObserver.pushed).extracting(ActivatedJob::getKey).containsExactly(1L);
  }

  private static final class TestStreamObserver extends ServerCallStreamObserver<ActivatedJob>
      implements StreamObserver<ActivatedJob> {
    private final List<ActivatedJob> pushed = new ArrayList<>();

    private boolean isReady = true;
    private Throwable failure;
    private Throwable error;

    @Override
    public boolean isCancelled() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setOnCancelHandler(final Runnable onCancelHandler) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setCompression(final String compression) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isReady() {
      return isReady;
    }

    @Override
    public void setOnReadyHandler(final Runnable onReadyHandler) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void request(final int count) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setMessageCompression(final boolean enable) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void disableAutoInboundFlowControl() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void onNext(final ActivatedJob value) {
      if (failure != null) {
        LangUtil.rethrowUnchecked(failure);
      }

      pushed.add(value);
    }

    @Override
    public void onError(final Throwable t) {
      error = t;
    }

    @Override
    public void onCompleted() {
      throw new UnsupportedOperationException();
    }
  }
}

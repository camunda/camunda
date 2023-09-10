/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.stream;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.impl.stream.ClientStreamAdapter.ClientStreamConsumerImpl;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivatedJob;
import io.camunda.zeebe.protocol.impl.stream.job.ActivatedJobImpl;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import org.agrona.LangUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

final class ClientStreamConsumerImplTest {
  @RegisterExtension
  public final ControlledActorSchedulerExtension scheduler =
      new ControlledActorSchedulerExtension();

  @Test
  void shouldRethrowExceptionOnPushFailure() {
    // given
    final var failure = new RuntimeException("failed");
    final var failingObserver = new FailingStreamObserver(failure);
    final var consumer = new ClientStreamConsumerImpl(failingObserver);
    scheduler.submitActor(consumer);
    scheduler.workUntilDone();

    // when
    final var result = consumer.push(BufferUtil.createCopy(new ActivatedJobImpl()));
    scheduler.workUntilDone();

    // then
    assertThat(failingObserver.error).isSameAs(failure);
    assertThat(result)
        .failsWithin(Duration.ZERO)
        .withThrowableThat()
        .havingRootCause()
        .isSameAs(failure);
  }

  private static final class FailingStreamObserver implements StreamObserver<ActivatedJob> {
    private final Throwable failure;
    private Throwable error;

    private FailingStreamObserver(final Throwable failure) {
      this.failure = failure;
    }

    @Override
    public void onNext(final ActivatedJob value) {
      LangUtil.rethrowUnchecked(failure);
    }

    @Override
    public void onError(final Throwable t) {
      error = t;
    }

    @Override
    public void onCompleted() {}
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.gateway.protocol.model.ActivatedProbeResult;
import io.camunda.gateway.protocol.model.ProbeActivationResult;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

final class ProbeActivationResponseObserverTest {

  private final CompletableFuture<ResponseEntity<Object>> result = new CompletableFuture<>();
  private final ProbeActivationResponseObserver observer =
      new ProbeActivationResponseObserver(result, "tenant-a");

  @Test
  void shouldAnswerWithTheProbesOfThePhysicalTenantOfThePoll() {
    // given
    final var batch =
        new ProbeBatchRecord()
            .addProbe(
                1L,
                new ProbeRecord()
                    .setProbeId("a")
                    .setTenantId("<default>")
                    .setWorker("w")
                    .setDeadline(10L)
                    .setInput(new UnsafeBuffer(MsgPackConverter.convertToMsgPack("{}"))));

    // when - the handler reuses its batch after delivering it
    observer.onNext(batch);
    batch.reset();
    observer.onCompleted();

    // then
    final var body = (ProbeActivationResult) result.join().getBody();
    assertThat(body.getProbes())
        .singleElement()
        .extracting(ActivatedProbeResult::getProbeKey, ActivatedProbeResult::getPhysicalTenantId)
        .containsExactly("1", "tenant-a");
  }

  @Test
  void shouldCountAsCancelledOnceTheWorkersRequestIsGone() {
    // given
    final var cancelled = new AtomicBoolean();
    observer.setCancelationHandler(() -> cancelled.set(true));

    // when - what the controller does when the request fails or times out
    result.cancel(false);
    observer.invokeCancelationHandler();

    // then - the handler yields what it collected instead of answering
    assertThat(observer.isCancelled()).isTrue();
    assertThat(cancelled).isTrue();
  }

  @Test
  void shouldFailToDeliverWhenTheRequestIsCancelledAfterTheLastCheck() {
    // given - the handler saw the poll as live, then the worker's request went away
    final var batch =
        new ProbeBatchRecord()
            .addProbe(
                1L,
                new ProbeRecord()
                    .setProbeId("a")
                    .setTenantId("<default>")
                    .setWorker("w")
                    .setDeadline(10L)
                    .setInput(new UnsafeBuffer(MsgPackConverter.convertToMsgPack("{}"))));
    assertThat(observer.isCancelled()).isFalse();
    observer.onNext(batch);
    result.cancel(false);

    // when / then - the failure makes the handler yield the probes instead of leaving them
    // activated until their deadline
    assertThatThrownBy(observer::onCompleted).isInstanceOf(IllegalStateException.class);
  }
}

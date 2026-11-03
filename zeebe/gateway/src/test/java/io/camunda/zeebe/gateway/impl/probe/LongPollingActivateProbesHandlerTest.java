/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.probe;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateProbesRequest;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.record.value.ProbeRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

final class LongPollingActivateProbesHandlerTest {

  private static final String PHYSICAL_TENANT = "default";
  private static final String TOPIC = PHYSICAL_TENANT + "-probesAvailable";
  private static final String AWS = "validate-configuration/io.camunda:aws-credential:1";
  private static final String SLACK = "validate-configuration/io.camunda:slack:1";
  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  @RegisterExtension
  final ControlledActorSchedulerExtension actorScheduler = new ControlledActorSchedulerExtension();

  private final StubbedBrokerClient brokerClient = new StubbedBrokerClient();

  /** Probe IDs with a probe waiting on the stubbed partitions; a poll collects each once. */
  private final Map<String, Integer> waitingProbes = new HashMap<>();

  private final AtomicLong nextProbeKey = new AtomicLong(1);
  private final List<ProbeBatchRecord> delivered = new ArrayList<>();
  private LongPollingActivateProbesHandler handler;

  @BeforeEach
  void setUp() {
    brokerClient.registerHandler(BrokerActivateProbesRequest.class, this::activate);
    handler = new LongPollingActivateProbesHandler(brokerClient, TIMEOUT);
    final var ready = new CompletableFuture<Void>();
    actorScheduler.submitActor(
        Actor.newActor()
            .name("probe-long-polling")
            .actorStartedHandler(handler.andThen(ignored -> ready.complete(null)))
            .build());
    actorScheduler.workUntilDone();
    ready.join();
  }

  @Test
  void shouldWakeAHeldPollOnAProbeOfItsFirstProbeId() {
    shouldWakeAHeldPollOnAProbeOf(AWS);
  }

  @Test
  void shouldWakeAHeldPollOnAProbeOfItsSecondProbeId() {
    shouldWakeAHeldPollOnAProbeOf(SLACK);
  }

  private void shouldWakeAHeldPollOnAProbeOf(final String probeId) {
    // given - a held poll for both probe IDs
    poll(AWS, SLACK);
    assertThat(delivered).isEmpty();

    // when
    waitingProbes.merge(probeId, 1, Integer::sum);
    brokerClient.notifyJobsAvailable(TOPIC, probeId);
    actorScheduler.workUntilDone();

    // then
    assertThat(delivered).hasSize(1);
    assertThat(delivered.getFirst().getProbes())
        .extracting(ProbeRecordValue::getProbeId)
        .containsExactly(probeId);
  }

  @Test
  void shouldStopWaitingUnderEveryProbeIdOnceWoken() {
    // given - a held poll for both probe IDs, woken by a probe of one of them
    poll(AWS, SLACK);
    waitingProbes.merge(AWS, 1, Integer::sum);
    brokerClient.notifyJobsAvailable(TOPIC, AWS);
    actorScheduler.workUntilDone();
    final int requestsAfterWake = brokerClient.getBrokerRequests().size();

    // when - the other probe ID announces a probe
    waitingProbes.merge(SLACK, 1, Integer::sum);
    brokerClient.notifyJobsAvailable(TOPIC, SLACK);
    actorScheduler.workUntilDone();

    // then - the poll was answered once and no longer waits for it
    assertThat(delivered).hasSize(1);
    assertThat(brokerClient.getBrokerRequests()).hasSize(requestsAfterWake);
  }

  @Test
  void shouldAnswerAHeldPollWithNoProbeAtItsTimeout() {
    // given
    poll(AWS, SLACK);

    // when
    actorScheduler.updateClock(TIMEOUT);
    actorScheduler.workUntilDone();

    // then
    assertThat(delivered).hasSize(1);
    assertThat(delivered.getFirst().probeKeys().size()).isZero();
  }

  private void poll(final String... probeIds) {
    final var request =
        new BrokerActivateProbesRequest(
            List.of(probeIds), "worker-a", 4, List.of(), TenantFilter.PROVIDED);
    request.setPartitionGroup(PHYSICAL_TENANT);
    handler.activateProbes(
        request,
        TIMEOUT.toMillis(),
        batch -> {
          final var copy = new ProbeBatchRecord();
          copy.copyFrom(batch);
          delivered.add(copy);
          return true;
        },
        error -> {
          throw new AssertionError(error);
        });
    actorScheduler.workUntilDone();
  }

  /** A partition hands out the waiting probes of the polled probe IDs. */
  private BrokerResponse<ProbeBatchRecord> activate(final BrokerActivateProbesRequest request) {
    final var batch = new ProbeBatchRecord().setProbeIds(request.getProbeIds());
    for (final String probeId : request.getProbeIds()) {
      if (waitingProbes.getOrDefault(probeId, 0) > 0) {
        waitingProbes.merge(probeId, -1, Integer::sum);
        batch.addProbe(nextProbeKey.getAndIncrement(), new ProbeRecord().setProbeId(probeId));
      }
    }
    return new BrokerResponse<>(batch, 1, 1);
  }
}

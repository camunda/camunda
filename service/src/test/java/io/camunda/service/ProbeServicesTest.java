/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.service.ProbeServices.ProbeActivationRequest;
import io.camunda.service.ProbeServices.ProbeAnswerRequest;
import io.camunda.service.ProbeServices.ProbeCreateRequest;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.dto.BrokerError;
import io.camunda.zeebe.broker.client.api.dto.BrokerErrorResponse;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejectionResponse;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.api.util.StubbedTopologyManager;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateProbesRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerProbeAnswerRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerProbeCreateRequest;
import io.camunda.zeebe.gateway.impl.job.ResponseObserver;
import io.camunda.zeebe.gateway.impl.probe.LongPollingActivateProbesHandler;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.protocol.record.value.ProbeEndReason;
import io.camunda.zeebe.protocol.record.value.ProbeOutcome;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

final class ProbeServicesTest {

  private static final String PHYSICAL_TENANT_ID = "tenant-a";
  private static final String PROBE_ID = "validate-configuration/io.camunda:aws-credential:1";
  private static final long ARRIVAL = 1_800_000_000_000L;
  private static final Duration CLUSTER_REQUEST_TIMEOUT = Duration.ofSeconds(15);

  private final AtomicLong now = new AtomicLong(ARRIVAL);
  private final InstantSource clock = () -> Instant.ofEpochMilli(now.get());
  private final List<Attempt> attempts = new ArrayList<>();
  private final CamundaAuthentication authentication = CamundaAuthentication.none();
  private BrokerClient brokerClient;
  private LongPollingActivateProbesHandler activateProbesHandler;
  private Function<BrokerProbeCreateRequest, BrokerResponse<ProbeRecord>> broker;

  @BeforeEach
  void setUp() {
    brokerClient = mock(BrokerClient.class);
    when(brokerClient.getTopologyManager()).thenReturn(new StubbedTopologyManager(3));
    when(brokerClient.sendRequest(any(BrokerProbeCreateRequest.class), any(Duration.class)))
        .thenAnswer(
            invocation -> {
              final BrokerProbeCreateRequest request = invocation.getArgument(0);
              final Duration timeout = invocation.getArgument(1);
              attempts.add(
                  new Attempt(
                      request.getPartitionId(),
                      timeout,
                      request.getRequestWriter().getDeadline(),
                      request.getRequestWriter().getRequestTimeout()));
              try {
                return CompletableFuture.completedFuture(broker.apply(request));
              } catch (final RuntimeException e) {
                return CompletableFuture.failedFuture(e.getCause());
              }
            });
    broker = request -> ended(request.getPartitionId());
    activateProbesHandler = mock(LongPollingActivateProbesHandler.class);
  }

  @Test
  void shouldSetTheDeadlineWhenTheRequestArrives() {
    // when
    services(CLUSTER_REQUEST_TIMEOUT).createProbe(create(10_000L), authentication).join();

    // then - the engine gets an absolute deadline (D49)
    assertThat(attempts)
        .singleElement()
        .satisfies(
            attempt -> {
              assertThat(attempt.deadline()).isEqualTo(ARRIVAL + 10_000);
              assertThat(attempt.requestTimeout()).isEqualTo(10_000);
            });
  }

  @Test
  void shouldUseTheClusterRequestTimeoutWhenTheRequestHasNone() {
    // given
    final var services = services(CLUSTER_REQUEST_TIMEOUT);

    // when
    services.createProbe(create(null), authentication).join();
    services.createProbe(create(0L), authentication).join();

    // then - the engine gets the effective request timeout, never 0
    assertThat(attempts)
        .hasSize(2)
        .allSatisfy(
            attempt -> {
              assertThat(attempt.requestTimeout()).isEqualTo(15_000);
              assertThat(attempt.deadline()).isEqualTo(ARRIVAL + 15_000);
            });
  }

  @Test
  void shouldCapTheClusterRequestTimeoutOfAProbe() {
    // when
    services(Duration.ofMinutes(1)).createProbe(create(null), authentication).join();

    // then
    assertThat(attempts).singleElement().extracting(Attempt::requestTimeout).isEqualTo(30_000L);
  }

  @Test
  void shouldWaitForTheBrokerUntilHalfASecondAfterTheDeadline() {
    // when
    services(CLUSTER_REQUEST_TIMEOUT).createProbe(create(10_000L), authentication).join();

    // then
    assertThat(attempts)
        .singleElement()
        .extracting(Attempt::timeout)
        .isEqualTo(Duration.ofMillis(10_500));
  }

  @Test
  void shouldGetTheEnginesAnswerOfAProbeWithoutWorker() {
    // given - the engine ends the probe at its deadline: no worker collected it (D40)
    broker =
        request -> {
          now.set(request.getRequestWriter().getDeadline());
          return new BrokerResponse<>(
              new ProbeRecord().setProbeId(PROBE_ID).setEndReason(ProbeEndReason.NO_WORKER),
              request.getPartitionId(),
              Protocol.encodePartitionId(request.getPartitionId(), 1));
        };

    // when
    final var result =
        services(CLUSTER_REQUEST_TIMEOUT).createProbe(create(10_000L), authentication).join();

    // then - the gateway still waits past the deadline, so the client gets 404, not 504
    assertThat(result.probe().getEndReason()).isEqualTo(ProbeEndReason.NO_WORKER);
    assertThat(attempts.getFirst().timeout()).isGreaterThan(Duration.ofMillis(10_000));
  }

  @Test
  void shouldTryAnotherPartitionWhenTheCommandWasNotWritten() {
    // given - the first partition is under backpressure, and answers after 2 s
    broker =
        request -> {
          if (attempts.size() == 1) {
            now.addAndGet(2_000);
            return new BrokerErrorResponse<>(
                new BrokerError(ErrorCode.RESOURCE_EXHAUSTED, "backpressure"));
          }
          return ended(request.getPartitionId());
        };

    // when
    final var result =
        services(CLUSTER_REQUEST_TIMEOUT).createProbe(create(10_000L), authentication).join();

    // then - same deadline, on another partition, waiting only until the same moment
    assertThat(attempts).hasSize(2);
    assertThat(attempts.get(1).partitionId()).isNotEqualTo(attempts.get(0).partitionId());
    assertThat(attempts.get(1).deadline()).isEqualTo(ARRIVAL + 10_000);
    assertThat(attempts.get(1).timeout()).isEqualTo(Duration.ofMillis(8_500));
    assertThat(result.probeKey())
        .isEqualTo(Protocol.encodePartitionId(attempts.get(1).partitionId(), 1));
  }

  @Test
  void shouldNotRetryAProbeThatTimedOutBetweenGatewayAndBroker() {
    // given - the command may have been written: retrying could create a second probe
    broker =
        request -> {
          throw new RuntimeException(new TimeoutException("no response"));
        };

    // when / then
    assertThatThrownBy(
            () ->
                services(CLUSTER_REQUEST_TIMEOUT)
                    .createProbe(create(10_000L), authentication)
                    .join())
        .cause()
        .isInstanceOfSatisfying(
            ServiceException.class,
            error -> assertThat(error.getStatus()).isEqualTo(Status.DEADLINE_EXCEEDED));
    assertThat(attempts).hasSize(1);
  }

  @Test
  void shouldSendProbesRoundRobinOverThePhysicalTenantsPartitions() {
    // given
    final var services = services(CLUSTER_REQUEST_TIMEOUT);

    // when
    for (int i = 0; i < 3; i++) {
      services.createProbe(create(10_000L), authentication).join();
    }

    // then
    assertThat(attempts).extracting(Attempt::partitionId).containsExactlyInAnyOrder(1, 2, 3);
  }

  @Test
  void shouldSendAnAnswerToThePartitionOfTheProbe() {
    // given
    final long probeKey = Protocol.encodePartitionId(2, 7);
    final var captor = ArgumentCaptor.forClass(BrokerProbeAnswerRequest.class);
    when(brokerClient.sendRequestWithRetry(captor.capture()))
        .thenReturn(CompletableFuture.completedFuture(new BrokerResponse<>(new ProbeRecord())));

    // when
    services(CLUSTER_REQUEST_TIMEOUT)
        .answerProbe(
            new ProbeAnswerRequest(probeKey, ProbeOutcome.ERROR, null, "unreachable"),
            authentication)
        .join();

    // then
    final var request = captor.getValue();
    assertThat(request.getPartitionId()).isEqualTo(2);
    assertThat(request.getKey()).isEqualTo(probeKey);
    assertThat(request.getPartitionGroup()).isEqualTo(PHYSICAL_TENANT_ID);
    assertThat(request.getRequestWriter().getOutcome()).isEqualTo(ProbeOutcome.ERROR);
    assertThat(request.getRequestWriter().getMessage()).isEqualTo("unreachable");
  }

  @Test
  void shouldKeepTheRejectionOfAProbeTooLargeForOneBatch() {
    // given
    broker =
        request ->
            new BrokerRejectionResponse<>(
                new BrokerRejection(
                    ProbeIntent.CREATE, -1, RejectionType.EXCEEDED_BATCH_RECORD_SIZE, ""));

    // when / then - still INTERNAL, as for any request, but the REST layer can tell it apart (D53)
    assertThatThrownBy(
            () ->
                services(CLUSTER_REQUEST_TIMEOUT)
                    .createProbe(create(10_000L), authentication)
                    .join())
        .cause()
        .isInstanceOfSatisfying(
            ServiceException.class,
            error -> {
              assertThat(error.getStatus()).isEqualTo(Status.INTERNAL);
              assertThat(error.getCause()).isInstanceOf(BrokerRejectionException.class);
            });
  }

  @Test
  void shouldKeepTheRejectionOfAnAnswerTooLargeForOneBatch() {
    // given
    when(brokerClient.sendRequestWithRetry(any(BrokerProbeAnswerRequest.class)))
        .thenReturn(
            CompletableFuture.completedFuture(
                new BrokerRejectionResponse<>(
                    new BrokerRejection(
                        ProbeIntent.ANSWER, 1, RejectionType.EXCEEDED_BATCH_RECORD_SIZE, ""))));

    // when / then
    assertThatThrownBy(() -> answer(ProbeOutcome.UNSUPPORTED).join())
        .cause()
        .isInstanceOfSatisfying(
            ServiceException.class,
            error -> {
              assertThat(error.getStatus()).isEqualTo(Status.INTERNAL);
              assertThat(error.getCause()).isInstanceOf(BrokerRejectionException.class);
            });
  }

  @Test
  void shouldMapTheOtherRejectionsOfAnAnswerAsAnyRequest() {
    // given
    when(brokerClient.sendRequestWithRetry(any(BrokerProbeAnswerRequest.class)))
        .thenReturn(
            CompletableFuture.completedFuture(
                new BrokerRejectionResponse<>(
                    new BrokerRejection(ProbeIntent.ANSWER, 1, RejectionType.NOT_FOUND, "ended"))),
            CompletableFuture.completedFuture(
                new BrokerErrorResponse<>(
                    new BrokerError(ErrorCode.RESOURCE_EXHAUSTED, "backpressure"))));

    // when / then
    assertThatThrownBy(() -> answer(ProbeOutcome.UNSUPPORTED).join())
        .cause()
        .isInstanceOfSatisfying(
            ServiceException.class,
            error -> {
              assertThat(error.getStatus()).isEqualTo(Status.NOT_FOUND);
              assertThat(error.getCause()).isNull();
            });
    assertThatThrownBy(() -> answer(ProbeOutcome.UNSUPPORTED).join())
        .cause()
        .isInstanceOfSatisfying(
            ServiceException.class,
            error -> assertThat(error.getStatus()).isEqualTo(Status.RESOURCE_EXHAUSTED));
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldHandAPollToTheLongPollingHandler() {
    // given
    final ResponseObserver<ProbeBatchRecord> observer = mock(ResponseObserver.class);
    final var captor = ArgumentCaptor.forClass(BrokerActivateProbesRequest.class);

    // when
    services(CLUSTER_REQUEST_TIMEOUT)
        .activateProbes(
            new ProbeActivationRequest(
                List.of(PROBE_ID), "worker-a", 5, null, List.of("t1"), TenantFilter.PROVIDED),
            observer,
            ignored -> {},
            authentication);

    // then - with this physical tenant, and the default long polling timeout
    verify(activateProbesHandler).activateProbes(captor.capture(), eq(observer), any(), eq(0L));
    final var request = captor.getValue();
    assertThat(request.getPartitionGroup()).isEqualTo(PHYSICAL_TENANT_ID);
    assertThat(request.getRequestWriter().getProbeIds()).containsExactly(PROBE_ID);
    assertThat(request.getRequestWriter().getWorker()).isEqualTo("worker-a");
    assertThat(request.getRequestWriter().getMaxProbesToActivate()).isEqualTo(5);
    assertThat(request.getRequestWriter().getTenantIds()).containsExactly("t1");
  }

  private ProbeServices services(final Duration clusterRequestTimeout) {
    final var executorProvider = mock(ApiServicesExecutorProvider.class);
    when(executorProvider.getExecutor()).thenReturn(ForkJoinPool.commonPool());
    final var converter = mock(BrokerRequestAuthorizationConverter.class);
    when(converter.convert(any(CamundaAuthentication.class))).thenReturn(Map.of());
    return new ProbeServices(
        PHYSICAL_TENANT_ID,
        brokerClient,
        mock(SecurityContextProvider.class),
        executorProvider,
        converter,
        activateProbesHandler,
        clusterRequestTimeout,
        clock);
  }

  private CompletableFuture<ProbeRecord> answer(final ProbeOutcome outcome) {
    return services(CLUSTER_REQUEST_TIMEOUT)
        .answerProbe(
            new ProbeAnswerRequest(Protocol.encodePartitionId(1, 1), outcome, null, null),
            authentication);
  }

  private static ProbeCreateRequest create(final Long requestTimeout) {
    return new ProbeCreateRequest(
        PROBE_ID, "={region: \"eu-west-1\"}", "<default>", requestTimeout);
  }

  private static BrokerResponse<ProbeRecord> ended(final int partitionId) {
    return new BrokerResponse<>(
        new ProbeRecord().setProbeId(PROBE_ID).setOutcome(ProbeOutcome.OK),
        partitionId,
        Protocol.encodePartitionId(partitionId, 1));
  }

  private record Attempt(int partitionId, Duration timeout, long deadline, long requestTimeout) {}
}

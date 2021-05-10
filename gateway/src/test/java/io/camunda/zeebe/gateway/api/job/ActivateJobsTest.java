/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.api.job;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.gateway.api.util.GatewayTest;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient.RequestHandler;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerRequest;
import io.camunda.zeebe.gateway.impl.broker.response.BrokerRejection;
import io.camunda.zeebe.gateway.impl.broker.response.BrokerRejectionResponse;
import io.camunda.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivatedJob;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.test.util.JsonUtil;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class ActivateJobsTest extends GatewayTest {

  public ActivateJobsTest(final boolean isLongPollingEnabled) {
    super(getConfig(isLongPollingEnabled));
  }

  @Parameters(name = "{index}: longPolling.enabled[{0}]")
  public static Iterable<Object[]> data() {
    return Arrays.asList(new Object[][] {{true}, {false}});
  }

  private static GatewayCfg getConfig(final boolean isLongPollingEnabled) {
    final var config = new GatewayCfg();
    config.getLongPolling().setEnabled(isLongPollingEnabled);
    return config;
  }

  @Test
  public void shouldMapRequestAndResponse() {
    // given
    final ActivateJobsStub stub = new ActivateJobsStub();
    stub.registerWith(brokerClient);

    final String jobType = "testJob";
    final String worker = "testWorker";
    final int maxJobsToActivate = 13;
    final Duration timeout = Duration.ofMinutes(12);
    final List<String> fetchVariables = Arrays.asList("foo", "bar", "baz");

    final ActivateJobsRequest request =
        ActivateJobsRequest.newBuilder()
            .setType(jobType)
            .setWorker(worker)
            .setMaxJobsToActivate(maxJobsToActivate)
            .setTimeout(timeout.toMillis())
            .addAllFetchVariable(fetchVariables)
            .build();

    stub.addAvailableJobs(jobType, maxJobsToActivate);

    // when
    final Iterator<ActivateJobsResponse> responses = client.activateJobs(request);

    // then
    assertThat(responses.hasNext()).isTrue();

    final ActivateJobsResponse response = responses.next();

    assertThat(response.getJobsCount()).isEqualTo(maxJobsToActivate);

    for (int i = 0; i < maxJobsToActivate; i++) {
      final ActivatedJob job = response.getJobs(i);
      assertThat(job.getKey())
          .isEqualTo(Protocol.encodePartitionId(Protocol.START_PARTITION_ID, i));
      assertThat(job.getType()).isEqualTo(jobType);
      assertThat(job.getWorker()).isEqualTo(worker);
      assertThat(job.getRetries()).isEqualTo(stub.getRetries());
      assertThat(job.getDeadline()).isEqualTo(stub.getDeadline());
      assertThat(job.getProcessInstanceKey()).isEqualTo(stub.getProcessInstanceKey());
      assertThat(job.getBpmnProcessId()).isEqualTo(stub.getBpmnProcessId());
      assertThat(job.getProcessDefinitionVersion()).isEqualTo(stub.getProcessDefinitionVersion());
      assertThat(job.getProcessDefinitionKey()).isEqualTo(stub.getProcessDefinitionKey());
      assertThat(job.getElementId()).isEqualTo(stub.getElementId());
      assertThat(job.getElementInstanceKey()).isEqualTo(stub.getElementInstanceKey());
      JsonUtil.assertEquality(job.getCustomHeaders(), stub.getCustomHeaders());
      JsonUtil.assertEquality(job.getVariables(), stub.getVariables());
    }

    final BrokerActivateJobsRequest brokerRequest = brokerClient.getSingleBrokerRequest();
    final JobBatchRecord brokerRequestValue = brokerRequest.getRequestWriter();
    assertThat(brokerRequestValue.getMaxJobsToActivate()).isEqualTo(maxJobsToActivate);
    assertThat(brokerRequestValue.getTypeBuffer()).isEqualTo(wrapString(jobType));
    assertThat(brokerRequestValue.getTimeout()).isEqualTo(timeout.toMillis());
    assertThat(brokerRequestValue.getWorkerBuffer()).isEqualTo(wrapString(worker));
    assertThat(brokerRequestValue.variables())
        .extracting(v -> BufferUtil.bufferAsString(v.getValue()))
        .containsExactlyInAnyOrderElementsOf(fetchVariables);
  }

  @Test
  public void shouldActivateJobsRoundRobin() {
    // given
    final ActivateJobsStub stub = new ActivateJobsStub();
    stub.registerWith(brokerClient);

    final String type = "test";
    final int maxJobsToActivate = 2;
    final ActivateJobsRequest request =
        ActivateJobsRequest.newBuilder()
            .setType(type)
            .setMaxJobsToActivate(maxJobsToActivate)
            .build();

    for (int partitionOffset = 0; partitionOffset < 3; partitionOffset++) {
      stub.addAvailableJobs(type, maxJobsToActivate);
      // when
      final Iterator<ActivateJobsResponse> responses = client.activateJobs(request);

      // then
      assertThat(responses.hasNext()).isTrue();
      final ActivateJobsResponse response = responses.next();

      for (final ActivatedJob activatedJob : response.getJobsList()) {
        assertThat(Protocol.decodePartitionId(activatedJob.getKey()))
            .isEqualTo(Protocol.START_PARTITION_ID + partitionOffset);
      }
    }
  }

  @Test
  public void shouldSendRejectionWithoutRetrying() {
    // given
    final RejectionType rejectionType = RejectionType.INVALID_ARGUMENT;
    final AtomicInteger callCounter = new AtomicInteger();

    brokerClient.registerHandler(
        BrokerActivateJobsRequest.class,
        (RequestHandler<BrokerRequest<?>, BrokerResponse<?>>)
            request -> {
              callCounter.incrementAndGet();
              return new BrokerRejectionResponse<>(
                  new BrokerRejection(Intent.UNKNOWN, 1, rejectionType, "expected"));
            });
    final ActivateJobsRequest request =
        ActivateJobsRequest.newBuilder().setType("").setMaxJobsToActivate(1).build();

    // when/then
    assertThatThrownBy(
            () -> {
              final Iterator<ActivateJobsResponse> responseIterator = client.activateJobs(request);
              responseIterator.hasNext();
            })
        .isInstanceOf(StatusRuntimeException.class)
        .extracting(t -> ((StatusRuntimeException) t).getStatus().getCode())
        .isEqualTo(Status.INVALID_ARGUMENT.getCode());
    assertThat(callCounter).hasValue(1);
  }
}

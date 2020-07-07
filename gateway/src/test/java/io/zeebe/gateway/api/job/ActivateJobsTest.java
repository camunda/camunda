/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.api.job;

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.gateway.api.util.GatewayTest;
import io.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.zeebe.gateway.impl.configuration.GatewayCfg;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivatedJob;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.zeebe.test.util.JsonUtil;
import io.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
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
      assertThat(job.getWorkflowInstanceKey()).isEqualTo(stub.getWorkflowInstanceKey());
      assertThat(job.getBpmnProcessId()).isEqualTo(stub.getBpmnProcessId());
      assertThat(job.getWorkflowDefinitionVersion()).isEqualTo(stub.getWorkflowDefinitionVersion());
      assertThat(job.getWorkflowKey()).isEqualTo(stub.getWorkflowKey());
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
}

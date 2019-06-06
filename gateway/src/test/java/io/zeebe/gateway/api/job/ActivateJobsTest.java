/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.gateway.api.job;

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.gateway.api.util.GatewayTest;
import io.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
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

public class ActivateJobsTest extends GatewayTest {

  @Test
  public void shouldMapRequestAndResponse() {
    // given
    final ActivateJobsStub stub = new ActivateJobsStub();
    stub.registerWith(gateway);

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
      assertThat(job.getJobHeaders().getWorkflowInstanceKey())
          .isEqualTo(stub.getWorkflowInstanceKey());
      assertThat(job.getJobHeaders().getBpmnProcessId()).isEqualTo(stub.getBpmnProcessId());
      assertThat(job.getJobHeaders().getWorkflowDefinitionVersion())
          .isEqualTo(stub.getWorkflowDefinitionVersion());
      assertThat(job.getJobHeaders().getWorkflowKey()).isEqualTo(stub.getWorkflowKey());
      assertThat(job.getJobHeaders().getElementId()).isEqualTo(stub.getElementId());
      assertThat(job.getJobHeaders().getElementInstanceKey())
          .isEqualTo(stub.getElementInstanceKey());
      JsonUtil.assertEquality(job.getCustomHeaders(), stub.getCustomHeaders());
      JsonUtil.assertEquality(job.getVariables(), stub.getVariables());
    }

    final BrokerActivateJobsRequest brokerRequest = gateway.getSingleBrokerRequest();
    final JobBatchRecord brokerRequestValue = brokerRequest.getRequestWriter();
    assertThat(brokerRequestValue.getMaxJobsToActivate()).isEqualTo(maxJobsToActivate);
    assertThat(brokerRequestValue.getType()).isEqualTo(wrapString(jobType));
    assertThat(brokerRequestValue.getTimeout()).isEqualTo(timeout.toMillis());
    assertThat(brokerRequestValue.getWorker()).isEqualTo(wrapString(worker));
    assertThat(brokerRequestValue.variables())
        .extracting(v -> BufferUtil.bufferAsString(v.getValue()))
        .containsExactlyInAnyOrderElementsOf(fetchVariables);
  }

  @Test
  public void shouldActivateJobsRoundRobin() {
    // given
    final ActivateJobsStub stub = new ActivateJobsStub();
    stub.registerWith(gateway);

    final ActivateJobsRequest request =
        ActivateJobsRequest.newBuilder().setType("test").setMaxJobsToActivate(2).build();

    for (int partitionOffset = 0; partitionOffset < 3; partitionOffset++) {
      // when
      final Iterator<ActivateJobsResponse> responses = client.activateJobs(request);

      // then
      assertThat(responses.hasNext()).isTrue();
      final ActivateJobsResponse response = responses.next();

      for (ActivatedJob activatedJob : response.getJobsList()) {
        assertThat(Protocol.decodePartitionId(activatedJob.getKey()))
            .isEqualTo(Protocol.START_PARTITION_ID + partitionOffset);
      }
    }
  }
}

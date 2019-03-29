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
package io.zeebe.client.job;

import static io.zeebe.test.util.JsonUtil.fromJsonAsMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zeebe.client.api.response.ActivateJobsResponse;
import io.zeebe.client.cmd.ClientException;
import io.zeebe.client.util.ClientTest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivatedJob;
import io.zeebe.gateway.protocol.GatewayOuterClass.JobHeaders;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class ActivateJobsTest extends ClientTest {

  @Test
  public void shouldActivateJobs() {
    // given
    final JobHeaders jobHeaders1 =
        JobHeaders.newBuilder()
            .setWorkflowInstanceKey(123)
            .setBpmnProcessId("test1")
            .setWorkflowDefinitionVersion(2)
            .setWorkflowKey(23)
            .setElementId("foo")
            .setElementInstanceKey(23213)
            .build();
    final ActivatedJob activatedJob1 =
        ActivatedJob.newBuilder()
            .setKey(12)
            .setType("foo")
            .setJobHeaders(jobHeaders1)
            .setCustomHeaders("{\"version\": 1}")
            .setWorker("worker1")
            .setRetries(34)
            .setDeadline(1231)
            .setVariables("{\"key\": \"val\"}")
            .build();

    final JobHeaders jobHeaders2 =
        JobHeaders.newBuilder()
            .setWorkflowInstanceKey(333)
            .setBpmnProcessId("test3")
            .setWorkflowDefinitionVersion(23)
            .setWorkflowKey(11)
            .setElementId("bar")
            .setElementInstanceKey(111)
            .build();
    final ActivatedJob activatedJob2 =
        ActivatedJob.newBuilder()
            .setKey(42)
            .setType("foo")
            .setJobHeaders(jobHeaders2)
            .setCustomHeaders("{\"key\": \"value\"}")
            .setWorker("worker1")
            .setRetries(334)
            .setDeadline(3131)
            .setVariables("{\"bar\": 3}")
            .build();

    gatewayService.onActivateJobsRequest(activatedJob1, activatedJob2);

    // when
    final ActivateJobsResponse response =
        client
            .newActivateJobsCommand()
            .jobType("foo")
            .maxJobsToActivate(3)
            .timeout(1000)
            .workerName("worker1")
            .send()
            .join();

    // then
    assertThat(response.getJobs()).hasSize(2);

    io.zeebe.client.api.response.ActivatedJob job = response.getJobs().get(0);
    assertThat(job.getKey()).isEqualTo(activatedJob1.getKey());
    assertThat(job.getType()).isEqualTo(activatedJob1.getType());
    assertThat(job.getHeaders()).isEqualToComparingFieldByField(activatedJob1.getJobHeaders());
    assertThat(job.getCustomHeaders()).isEqualTo(fromJsonAsMap(activatedJob1.getCustomHeaders()));
    assertThat(job.getWorker()).isEqualTo(activatedJob1.getWorker());
    assertThat(job.getRetries()).isEqualTo(activatedJob1.getRetries());
    assertThat(job.getDeadline()).isEqualTo(Instant.ofEpochMilli(activatedJob1.getDeadline()));
    assertThat(job.getVariables()).isEqualTo(activatedJob1.getVariables());

    job = response.getJobs().get(1);
    assertThat(job.getKey()).isEqualTo(activatedJob2.getKey());
    assertThat(job.getType()).isEqualTo(activatedJob2.getType());
    assertThat(job.getHeaders()).isEqualToComparingFieldByField(activatedJob2.getJobHeaders());
    assertThat(job.getCustomHeaders()).isEqualTo(fromJsonAsMap(activatedJob2.getCustomHeaders()));
    assertThat(job.getWorker()).isEqualTo(activatedJob2.getWorker());
    assertThat(job.getRetries()).isEqualTo(activatedJob2.getRetries());
    assertThat(job.getDeadline()).isEqualTo(Instant.ofEpochMilli(activatedJob2.getDeadline()));
    assertThat(job.getVariables()).isEqualTo(activatedJob2.getVariables());

    final ActivateJobsRequest request = gatewayService.getLastRequest();
    assertThat(request.getType()).isEqualTo("foo");
    assertThat(request.getMaxJobsToActivate()).isEqualTo(3);
    assertThat(request.getTimeout()).isEqualTo(1000);
    assertThat(request.getWorker()).isEqualTo("worker1");
  }

  @Test
  public void shouldSetTimeoutFromDuration() {
    // given
    final Duration timeout = Duration.ofMinutes(2);

    // when
    client
        .newActivateJobsCommand()
        .jobType("foo")
        .maxJobsToActivate(3)
        .timeout(timeout)
        .send()
        .join();

    // then
    final ActivateJobsRequest request = gatewayService.getLastRequest();
    assertThat(request.getTimeout()).isEqualTo(timeout.toMillis());
  }

  @Test
  public void shouldSetFetchVariables() {
    // given
    final List<String> fetchVariables = Arrays.asList("foo", "bar", "baz");

    // when
    client
        .newActivateJobsCommand()
        .jobType("foo")
        .maxJobsToActivate(3)
        .fetchVariables(fetchVariables)
        .send()
        .join();

    // then
    final ActivateJobsRequest request = gatewayService.getLastRequest();
    assertThat(request.getFetchVariableList()).containsExactlyInAnyOrderElementsOf(fetchVariables);
  }

  @Test
  public void shouldSetFetchVariablesAsVargs() {
    // given
    final String[] fetchVariables = new String[] {"foo", "bar", "baz"};

    // when
    client
        .newActivateJobsCommand()
        .jobType("foo")
        .maxJobsToActivate(3)
        .fetchVariables(fetchVariables)
        .send()
        .join();

    // then
    final ActivateJobsRequest request = gatewayService.getLastRequest();
    assertThat(request.getFetchVariableList()).containsExactlyInAnyOrder(fetchVariables);
  }

  @Test
  public void shouldSetDefaultValues() {
    // when
    client.newActivateJobsCommand().jobType("foo").maxJobsToActivate(3).send().join();

    // then
    final ActivateJobsRequest request = gatewayService.getLastRequest();
    assertThat(request.getTimeout())
        .isEqualTo(client.getConfiguration().getDefaultJobTimeout().toMillis());
    assertThat(request.getWorker()).isEqualTo(client.getConfiguration().getDefaultJobWorkerName());
  }

  @Test
  public void shouldRaiseExceptionOnError() {
    // given
    gatewayService.errorOnRequest(
        ActivateJobsRequest.class, () -> new ClientException("Invalid request"));

    // when
    assertThatThrownBy(
            () -> client.newActivateJobsCommand().jobType("foo").maxJobsToActivate(3).send().join())
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("Invalid request");
  }
}

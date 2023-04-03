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
package io.camunda.zeebe.client.job;

import static io.camunda.zeebe.client.util.JsonUtil.fromJsonAsMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.api.response.ActivateJobsResponse;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.impl.ZeebeObjectMapper;
import io.camunda.zeebe.client.impl.response.ActivatedJobImpl;
import io.camunda.zeebe.client.util.ClientTest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivatedJob;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public final class ActivateJobsTest extends ClientTest {

  @Test
  public void shouldActivateJobs() {
    // given
    final ActivatedJob activatedJob1 =
        ActivatedJob.newBuilder()
            .setKey(12)
            .setType("foo")
            .setProcessInstanceKey(123)
            .setBpmnProcessId("test1")
            .setProcessDefinitionVersion(2)
            .setProcessDefinitionKey(23)
            .setElementId("foo")
            .setElementInstanceKey(23213)
            .setCustomHeaders("{\"version\": \"1\"}")
            .setWorker("worker1")
            .setRetries(34)
            .setDeadline(1231)
            .setVariables("{\"key\": \"val\"}")
            .build();

    final ActivatedJob activatedJob2 =
        ActivatedJob.newBuilder()
            .setKey(42)
            .setType("foo")
            .setProcessInstanceKey(333)
            .setBpmnProcessId("test3")
            .setProcessDefinitionVersion(23)
            .setProcessDefinitionKey(11)
            .setElementId("bar")
            .setElementInstanceKey(111)
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
            .timeout(Duration.ofMillis(1000))
            .workerName("worker1")
            .send()
            .join();

    // then
    assertThat(response.getJobs()).hasSize(2);

    io.camunda.zeebe.client.api.response.ActivatedJob job = response.getJobs().get(0);
    assertThat(job.getKey()).isEqualTo(activatedJob1.getKey());
    assertThat(job.getType()).isEqualTo(activatedJob1.getType());
    assertThat(job.getBpmnProcessId()).isEqualTo(activatedJob1.getBpmnProcessId());
    assertThat(job.getElementId()).isEqualTo(activatedJob1.getElementId());
    assertThat(job.getElementInstanceKey()).isEqualTo(activatedJob1.getElementInstanceKey());
    assertThat(job.getProcessDefinitionVersion())
        .isEqualTo(activatedJob1.getProcessDefinitionVersion());
    assertThat(job.getProcessDefinitionKey()).isEqualTo(activatedJob1.getProcessDefinitionKey());
    assertThat(job.getProcessInstanceKey()).isEqualTo(activatedJob1.getProcessInstanceKey());
    assertThat(job.getCustomHeaders()).isEqualTo(fromJsonAsMap(activatedJob1.getCustomHeaders()));
    assertThat(job.getWorker()).isEqualTo(activatedJob1.getWorker());
    assertThat(job.getRetries()).isEqualTo(activatedJob1.getRetries());
    assertThat(job.getDeadline()).isEqualTo(activatedJob1.getDeadline());
    assertThat(job.getVariables()).isEqualTo(activatedJob1.getVariables());

    job = response.getJobs().get(1);
    assertThat(job.getKey()).isEqualTo(activatedJob2.getKey());
    assertThat(job.getType()).isEqualTo(activatedJob2.getType());
    assertThat(job.getBpmnProcessId()).isEqualTo(activatedJob2.getBpmnProcessId());
    assertThat(job.getElementId()).isEqualTo(activatedJob2.getElementId());
    assertThat(job.getElementInstanceKey()).isEqualTo(activatedJob2.getElementInstanceKey());
    assertThat(job.getProcessDefinitionVersion())
        .isEqualTo(activatedJob2.getProcessDefinitionVersion());
    assertThat(job.getProcessDefinitionKey()).isEqualTo(activatedJob2.getProcessDefinitionKey());
    assertThat(job.getProcessInstanceKey()).isEqualTo(activatedJob2.getProcessInstanceKey());
    assertThat(job.getCustomHeaders()).isEqualTo(fromJsonAsMap(activatedJob2.getCustomHeaders()));
    assertThat(job.getWorker()).isEqualTo(activatedJob2.getWorker());
    assertThat(job.getRetries()).isEqualTo(activatedJob2.getRetries());
    assertThat(job.getDeadline()).isEqualTo(activatedJob2.getDeadline());
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
    assertThat(request.getRequestTimeout())
        .isEqualTo(client.getConfiguration().getDefaultRequestTimeout().toMillis());
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

  @Test
  public void shouldSetRequestTimeout() {
    // given
    final Duration requestTimeout = Duration.ofHours(124);

    // when
    client
        .newActivateJobsCommand()
        .jobType("foo")
        .maxJobsToActivate(3)
        .requestTimeout(requestTimeout)
        .send()
        .join();
    final ActivateJobsRequest request = gatewayService.getLastRequest();

    // then
    assertThat(request.getRequestTimeout()).isEqualTo(requestTimeout.toMillis());
  }

  @Test
  public void shouldDeserializePartiallyToPojo() {
    // given
    final ActivatedJobImpl activatedJob =
        new ActivatedJobImpl(
            new ZeebeObjectMapper(),
            ActivatedJob.newBuilder()
                .setCustomHeaders("{}")
                .setVariables("{\"a\": 1, \"b\": 2}")
                .build());

    // when
    final VariablesPojo variablesPojo = activatedJob.getVariablesAsType(VariablesPojo.class);

    // then
    assertThat(variablesPojo.getA()).isEqualTo(1);
  }

  static class VariablesPojo {

    int a;

    public int getA() {
      return a;
    }

    public VariablesPojo setA(final int a) {
      this.a = a;
      return this;
    }
  }
}

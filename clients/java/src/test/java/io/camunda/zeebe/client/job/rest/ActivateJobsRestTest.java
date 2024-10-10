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
package io.camunda.zeebe.client.job.rest;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.client.api.command.ActivateJobsCommandStep1.ActivateJobsCommandStep3;
import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.api.command.ProblemException;
import io.camunda.zeebe.client.api.response.ActivateJobsResponse;
import io.camunda.zeebe.client.impl.ZeebeClientBuilderImpl;
import io.camunda.zeebe.client.impl.ZeebeObjectMapper;
import io.camunda.zeebe.client.impl.response.ActivatedJobImpl;
import io.camunda.zeebe.client.protocol.rest.ActivatedJob;
import io.camunda.zeebe.client.protocol.rest.JobActivationRequest;
import io.camunda.zeebe.client.protocol.rest.JobActivationResponse;
import io.camunda.zeebe.client.protocol.rest.ProblemDetail;
import io.camunda.zeebe.client.util.ClientRestTest;
import io.camunda.zeebe.client.util.RestGatewayPaths;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public final class ActivateJobsRestTest extends ClientRestTest {

  @Test
  void shouldActivateJobs() {
    // given
    final ActivatedJob activatedJob1 =
        new ActivatedJob()
            .jobKey(12L)
            .type("foo")
            .processInstanceKey(123L)
            .processDefinitionId("test1")
            .processDefinitionVersion(2)
            .processDefinitionKey(23L)
            .elementId("foo")
            .elementInstanceKey(23213L)
            .customHeaders(singletonMap("version", "1"))
            .worker("worker1")
            .retries(34)
            .deadline(1231L)
            .variables(singletonMap("key", "val"))
            .tenantId("test-tenant-1");

    final ActivatedJob activatedJob2 =
        new ActivatedJob()
            .jobKey(42L)
            .type("foo")
            .processInstanceKey(333L)
            .processDefinitionId("test3")
            .processDefinitionVersion(23)
            .processDefinitionKey(11L)
            .elementId("bar")
            .elementInstanceKey(111L)
            .customHeaders(singletonMap("key", "value"))
            .worker("worker1")
            .retries(334)
            .deadline(3131L)
            .variables(singletonMap("bar", 3))
            .tenantId("test-tenant-2");

    gatewayService.onActivateJobsRequest(
        new JobActivationResponse().addJobsItem(activatedJob1).addJobsItem(activatedJob2));

    // when
    final ActivateJobsResponse response =
        client
            .newActivateJobsCommand()
            .jobType("foo")
            .maxJobsToActivate(3)
            .timeout(Duration.ofMillis(1000))
            .workerName("worker1")
            .tenantIds("test-tenant-1", "test-tenant-2")
            .send()
            .join();

    // then
    assertThat(response.getJobs()).hasSize(2);

    io.camunda.zeebe.client.api.response.ActivatedJob job = response.getJobs().get(0);
    assertThat(job.getKey()).isEqualTo(activatedJob1.getJobKey());
    assertThat(job.getType()).isEqualTo(activatedJob1.getType());
    assertThat(job.getBpmnProcessId()).isEqualTo(activatedJob1.getProcessDefinitionId());
    assertThat(job.getElementId()).isEqualTo(activatedJob1.getElementId());
    assertThat(job.getElementInstanceKey()).isEqualTo(activatedJob1.getElementInstanceKey());
    assertThat(job.getProcessDefinitionVersion())
        .isEqualTo(activatedJob1.getProcessDefinitionVersion());
    assertThat(job.getProcessDefinitionKey()).isEqualTo(activatedJob1.getProcessDefinitionKey());
    assertThat(job.getProcessInstanceKey()).isEqualTo(activatedJob1.getProcessInstanceKey());
    assertThat(job.getCustomHeaders()).isEqualTo(activatedJob1.getCustomHeaders());
    assertThat(job.getWorker()).isEqualTo(activatedJob1.getWorker());
    assertThat(job.getRetries()).isEqualTo(activatedJob1.getRetries());
    assertThat(job.getDeadline()).isEqualTo(activatedJob1.getDeadline());
    assertThat(job.getVariablesAsMap()).isEqualTo(activatedJob1.getVariables());
    assertThat(job.getTenantId()).isEqualTo(activatedJob1.getTenantId());

    job = response.getJobs().get(1);
    assertThat(job.getKey()).isEqualTo(activatedJob2.getJobKey());
    assertThat(job.getType()).isEqualTo(activatedJob2.getType());
    assertThat(job.getBpmnProcessId()).isEqualTo(activatedJob2.getProcessDefinitionId());
    assertThat(job.getElementId()).isEqualTo(activatedJob2.getElementId());
    assertThat(job.getElementInstanceKey()).isEqualTo(activatedJob2.getElementInstanceKey());
    assertThat(job.getProcessDefinitionVersion())
        .isEqualTo(activatedJob2.getProcessDefinitionVersion());
    assertThat(job.getProcessDefinitionKey()).isEqualTo(activatedJob2.getProcessDefinitionKey());
    assertThat(job.getProcessInstanceKey()).isEqualTo(activatedJob2.getProcessInstanceKey());
    assertThat(job.getCustomHeaders()).isEqualTo(activatedJob2.getCustomHeaders());
    assertThat(job.getWorker()).isEqualTo(activatedJob2.getWorker());
    assertThat(job.getRetries()).isEqualTo(activatedJob2.getRetries());
    assertThat(job.getDeadline()).isEqualTo(activatedJob2.getDeadline());
    assertThat(job.getVariablesAsMap()).isEqualTo(activatedJob2.getVariables());
    assertThat(job.getTenantId()).isEqualTo(activatedJob2.getTenantId());

    final JobActivationRequest request = gatewayService.getLastRequest(JobActivationRequest.class);
    assertThat(request.getType()).isEqualTo("foo");
    assertThat(request.getMaxJobsToActivate()).isEqualTo(3);
    assertThat(request.getTimeout()).isEqualTo(1000);
    assertThat(request.getWorker()).isEqualTo("worker1");
  }

  @Test
  void shouldSetTimeoutFromDuration() {
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
    final JobActivationRequest request = gatewayService.getLastRequest(JobActivationRequest.class);
    assertThat(request.getTimeout()).isEqualTo(timeout.toMillis());
  }

  @Test
  void shouldSetFetchVariables() {
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
    final JobActivationRequest request = gatewayService.getLastRequest(JobActivationRequest.class);

    assertThat(request.getFetchVariable()).containsExactlyInAnyOrderElementsOf(fetchVariables);
  }

  @Test
  void shouldSetFetchVariablesAsVargs() {
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
    final JobActivationRequest request = gatewayService.getLastRequest(JobActivationRequest.class);
    assertThat(request.getFetchVariable()).containsExactlyInAnyOrder(fetchVariables);
  }

  @Test
  void shouldSetDefaultValues() {
    // when
    client.newActivateJobsCommand().jobType("foo").maxJobsToActivate(3).send().join();

    // then
    final JobActivationRequest request = gatewayService.getLastRequest(JobActivationRequest.class);
    assertThat(request.getTimeout())
        .isEqualTo(client.getConfiguration().getDefaultJobTimeout().toMillis());

    assertThat(request.getWorker()).isEqualTo(client.getConfiguration().getDefaultJobWorkerName());
    assertThat(request.getRequestTimeout())
        .isEqualTo(client.getConfiguration().getDefaultRequestTimeout().toMillis());
  }

  @Test
  void shouldRaiseExceptionOnError() {
    // given
    gatewayService.errorOnRequest(
        RestGatewayPaths.getJobActivationUrl(),
        () -> new ProblemDetail().title("Invalid request").status(400));

    // when
    assertThatThrownBy(
            () -> client.newActivateJobsCommand().jobType("foo").maxJobsToActivate(3).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Invalid request");
  }

  @Test
  void shouldSetRequestTimeout() {
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
    final JobActivationRequest request = gatewayService.getLastRequest(JobActivationRequest.class);

    // then
    assertThat(request.getRequestTimeout()).isEqualTo(requestTimeout.toMillis());
  }

  @Test
  void shouldDeserializePartiallyToPojo() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("a", 1);
    variables.put("b", 2);
    final ActivatedJobImpl activatedJob =
        new ActivatedJobImpl(
            new ZeebeObjectMapper(),
            new ActivatedJob().customHeaders(new HashMap<>()).variables(variables));

    // when
    final VariablesPojo variablesPojo = activatedJob.getVariablesAsType(VariablesPojo.class);

    // then
    assertThat(variablesPojo.getA()).isEqualTo(1);
  }

  @Test
  void shouldAllowSpecifyingTenantIdsAsList() {
    // given
    client
        .newActivateJobsCommand()
        .jobType("foo")
        .maxJobsToActivate(3)
        .tenantIds(Arrays.asList("tenant1", "tenant2"))
        .send()
        .join();

    // when
    final JobActivationRequest request = gatewayService.getLastRequest(JobActivationRequest.class);

    // then
    assertThat(request.getTenantIds()).containsExactlyInAnyOrder("tenant1", "tenant2");
  }

  @Test
  void shouldAllowSpecifyingTenantIdsAsVarArgs() {
    // given
    client
        .newActivateJobsCommand()
        .jobType("foo")
        .maxJobsToActivate(3)
        .tenantIds("tenant1", "tenant2")
        .send()
        .join();

    // when
    final JobActivationRequest request = gatewayService.getLastRequest(JobActivationRequest.class);

    // then
    assertThat(request.getTenantIds()).containsExactlyInAnyOrder("tenant1", "tenant2");
  }

  @Test
  void shouldAllowSpecifyingTenantIds() {
    // given
    client
        .newActivateJobsCommand()
        .jobType("foo")
        .maxJobsToActivate(3)
        .tenantId("tenant1")
        .tenantId("tenant2")
        .tenantId("tenant2")
        .send()
        .join();

    // when
    final JobActivationRequest request = gatewayService.getLastRequest(JobActivationRequest.class);

    // then
    assertThat(request.getTenantIds()).containsExactlyInAnyOrder("tenant1", "tenant2");
  }

  // Regression: https://github.com/camunda/camunda/issues/17513
  @Test
  void shouldNotAccumulateTenantsOnSuccessiveOpen() {
    // given
    final ActivateJobsCommandStep3 command =
        client
            .newActivateJobsCommand()
            .jobType("foo")
            .maxJobsToActivate(3)
            .tenantId("tenant1")
            .tenantId("tenant2")
            .tenantId("tenant2");

    // when
    command.send().join();
    command.send().join();
    final JobActivationRequest request = gatewayService.getLastRequest(JobActivationRequest.class);

    // then
    assertThat(request.getTenantIds()).containsExactlyInAnyOrder("tenant1", "tenant2");
  }

  @Test
  void shouldGetSingleVariable() {
    // given
    final Map<String, Object> variablesJob1 = new HashMap<>();
    variablesJob1.put("key", "val");
    variablesJob1.put("foo", "bar");
    variablesJob1.put("joe", "doe");
    final Map<String, Object> variablesJob2 = new HashMap<>();
    variablesJob2.put("key", "val2");
    variablesJob2.put("foo", "bar2");
    variablesJob2.put("joe", "doe2");

    final ActivatedJob activatedJob1 = new ActivatedJob().variables(variablesJob1);
    final ActivatedJob activatedJob2 = new ActivatedJob().variables(variablesJob2);

    gatewayService.onActivateJobsRequest(
        new JobActivationResponse().addJobsItem(activatedJob1).addJobsItem(activatedJob2));

    // when
    final ActivateJobsResponse response =
        client.newActivateJobsCommand().jobType("foo").maxJobsToActivate(3).send().join();

    assertThat(response.getJobs()).hasSize(2);

    final io.camunda.zeebe.client.api.response.ActivatedJob job1 = response.getJobs().get(0);
    assertThat(job1.getVariable("key")).isEqualTo("val");
    assertThat(job1.getVariable("foo")).isEqualTo("bar");
    assertThat(job1.getVariable("joe")).isEqualTo("doe");

    final io.camunda.zeebe.client.api.response.ActivatedJob job2 = response.getJobs().get(1);
    assertThat(job2.getVariable("key")).isEqualTo("val2");
    assertThat(job2.getVariable("foo")).isEqualTo("bar2");
    assertThat(job2.getVariable("joe")).isEqualTo("doe2");
  }

  @Test
  void shouldThrowAnErrorIfVariableNameIsNotPresent() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("key", "val");
    variables.put("foo", "bar");
    variables.put("joe", "doe");
    final ActivatedJob activatedJob1 = new ActivatedJob().variables(variables);

    gatewayService.onActivateJobsRequest(new JobActivationResponse().addJobsItem(activatedJob1));

    // when
    final ActivateJobsResponse response =
        client.newActivateJobsCommand().jobType("foo").maxJobsToActivate(3).send().join();

    assertThat(response.getJobs()).hasSize(1);

    assertThatThrownBy(() -> response.getJobs().get(0).getVariable("notPresentName"))
        .isInstanceOf(ClientException.class);
  }

  @Test
  void shouldReturnNullIfVariableValueIsNull() {
    final Map<String, Object> variables = singletonMap("key", null);
    // given
    final ActivatedJob activatedJob1 = new ActivatedJob().variables(variables);

    gatewayService.onActivateJobsRequest(new JobActivationResponse().addJobsItem(activatedJob1));

    // when
    final ActivateJobsResponse response =
        client.newActivateJobsCommand().jobType("foo").maxJobsToActivate(3).send().join();

    assertThat(response.getJobs()).hasSize(1);

    assertThat(response.getJobs().get(0).getVariable("key")).isNull();
  }

  @Test
  void shouldSetDefaultWorkerNameWhenMissingInCommand() {
    // when
    client.newActivateJobsCommand().jobType("foo").maxJobsToActivate(1).send().join();

    // then
    final JobActivationRequest request = gatewayService.getLastRequest(JobActivationRequest.class);
    assertThat(request.getWorker()).isEqualTo(ZeebeClientBuilderImpl.DEFAULT_JOB_WORKER_NAME_VAR);
  }

  private static final class VariablesPojo {

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

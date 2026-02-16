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
package io.camunda.client.job.rest;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.api.command.ActivateJobsCommandStep1.ActivateJobsCommandStep3;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.command.enums.TenantFilter;
import io.camunda.client.api.response.ActivateJobsResponse;
import io.camunda.client.api.search.enums.JobKind;
import io.camunda.client.api.search.enums.ListenerEventType;
import io.camunda.client.impl.CamundaClientBuilderImpl;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.client.impl.response.ActivatedJobImpl;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.ActivatedJobResult;
import io.camunda.client.protocol.rest.JobActivationRequest;
import io.camunda.client.protocol.rest.JobActivationResult;
import io.camunda.client.protocol.rest.JobKindEnum;
import io.camunda.client.protocol.rest.JobListenerEventTypeEnum;
import io.camunda.client.protocol.rest.ProblemDetail;
import io.camunda.client.protocol.rest.TenantFilterEnum;
import io.camunda.client.protocol.rest.UserTaskProperties;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayPaths;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public final class ActivateJobsRestTest extends ClientRestTest {

  @Test
  void shouldActivateJobs() {
    // given
    final ActivatedJobResult activatedJob1 =
        new ActivatedJobResult()
            .jobKey("12")
            .type("foo")
            .processInstanceKey("123")
            .processDefinitionId("test1")
            .processDefinitionVersion(2)
            .processDefinitionKey("23")
            .elementId("foo")
            .elementInstanceKey("23213")
            .customHeaders(singletonMap("version", "1"))
            .worker("worker1")
            .retries(34)
            .deadline(1231L)
            .variables(singletonMap("key", "val"))
            .tenantId("test-tenant-1")
            .kind(JobKindEnum.BPMN_ELEMENT)
            .listenerEventType(JobListenerEventTypeEnum.START)
            .rootProcessInstanceKey("321");

    final ActivatedJobResult activatedJob2 =
        new ActivatedJobResult()
            .jobKey("42")
            .type("foo")
            .processInstanceKey("333")
            .processDefinitionId("test3")
            .processDefinitionVersion(23)
            .processDefinitionKey("11")
            .elementId("bar")
            .elementInstanceKey("111")
            .customHeaders(singletonMap("key", "value"))
            .worker("worker1")
            .retries(334)
            .deadline(3131L)
            .variables(singletonMap("bar", 3))
            .tenantId("test-tenant-2")
            .kind(JobKindEnum.BPMN_ELEMENT)
            .listenerEventType(JobListenerEventTypeEnum.END)
            .rootProcessInstanceKey("444");

    gatewayService.onActivateJobsRequest(
        new JobActivationResult().addJobsItem(activatedJob1).addJobsItem(activatedJob2));

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

    io.camunda.client.api.response.ActivatedJob job = response.getJobs().get(0);
    assertThat(String.valueOf(job.getKey())).isEqualTo(activatedJob1.getJobKey());
    assertThat(job.getType()).isEqualTo(activatedJob1.getType());
    assertThat(job.getBpmnProcessId()).isEqualTo(activatedJob1.getProcessDefinitionId());
    assertThat(job.getElementId()).isEqualTo(activatedJob1.getElementId());
    assertThat(String.valueOf(job.getElementInstanceKey()))
        .isEqualTo(activatedJob1.getElementInstanceKey());
    assertThat(job.getProcessDefinitionVersion())
        .isEqualTo(activatedJob1.getProcessDefinitionVersion());
    assertThat(String.valueOf(job.getProcessDefinitionKey()))
        .isEqualTo(activatedJob1.getProcessDefinitionKey());
    assertThat(String.valueOf(job.getProcessInstanceKey()))
        .isEqualTo(activatedJob1.getProcessInstanceKey());
    assertThat(String.valueOf(job.getRootProcessInstanceKey()))
        .isEqualTo(activatedJob1.getRootProcessInstanceKey());
    assertThat(job.getCustomHeaders()).isEqualTo(activatedJob1.getCustomHeaders());
    assertThat(job.getWorker()).isEqualTo(activatedJob1.getWorker());
    assertThat(job.getRetries()).isEqualTo(activatedJob1.getRetries());
    assertThat(job.getDeadline()).isEqualTo(activatedJob1.getDeadline());
    assertThat(job.getVariablesAsMap()).isEqualTo(activatedJob1.getVariables());
    assertThat(job.getUserTask()).isNull();
    assertThat(job.getTenantId()).isEqualTo(activatedJob1.getTenantId());
    assertThat(job.getKind()).isEqualTo(EnumUtil.convert(activatedJob1.getKind(), JobKind.class));
    assertThat(job.getListenerEventType())
        .isEqualTo(EnumUtil.convert(activatedJob1.getListenerEventType(), ListenerEventType.class));

    job = response.getJobs().get(1);
    assertThat(String.valueOf(job.getKey())).isEqualTo(activatedJob2.getJobKey());
    assertThat(job.getType()).isEqualTo(activatedJob2.getType());
    assertThat(job.getBpmnProcessId()).isEqualTo(activatedJob2.getProcessDefinitionId());
    assertThat(job.getElementId()).isEqualTo(activatedJob2.getElementId());
    assertThat(String.valueOf(job.getElementInstanceKey()))
        .isEqualTo(activatedJob2.getElementInstanceKey());
    assertThat(job.getProcessDefinitionVersion())
        .isEqualTo(activatedJob2.getProcessDefinitionVersion());
    assertThat(String.valueOf(job.getProcessDefinitionKey()))
        .isEqualTo(activatedJob2.getProcessDefinitionKey());
    assertThat(String.valueOf(job.getRootProcessInstanceKey()))
        .isEqualTo(activatedJob2.getRootProcessInstanceKey());
    assertThat(String.valueOf(job.getProcessInstanceKey()))
        .isEqualTo(activatedJob2.getProcessInstanceKey());
    assertThat(job.getCustomHeaders()).isEqualTo(activatedJob2.getCustomHeaders());
    assertThat(job.getWorker()).isEqualTo(activatedJob2.getWorker());
    assertThat(job.getRetries()).isEqualTo(activatedJob2.getRetries());
    assertThat(job.getDeadline()).isEqualTo(activatedJob2.getDeadline());
    assertThat(job.getVariablesAsMap()).isEqualTo(activatedJob2.getVariables());
    assertThat(job.getUserTask()).isNull();
    assertThat(job.getTenantId()).isEqualTo(activatedJob2.getTenantId());
    assertThat(job.getKind()).isEqualTo(EnumUtil.convert(activatedJob2.getKind(), JobKind.class));
    assertThat(job.getListenerEventType())
        .isEqualTo(EnumUtil.convert(activatedJob2.getListenerEventType(), ListenerEventType.class));

    final JobActivationRequest request = gatewayService.getLastRequest(JobActivationRequest.class);
    assertThat(request.getType()).isEqualTo("foo");
    assertThat(request.getMaxJobsToActivate()).isEqualTo(3);
    assertThat(request.getTimeout()).isEqualTo(1000);
    assertThat(request.getWorker()).isEqualTo("worker1");
  }

  @ParameterizedTest
  @MethodSource("userTaskPropertiesProvider")
  public void shouldActivateJobsWithUserTaskProperties(
      final ActivateJobWithUserTaskPropsTestCase testCase) {

    // given
    final ActivatedJobResult activatedJob =
        new ActivatedJobResult().userTask(testCase.providedProperties);

    gatewayService.onActivateJobsRequest(new JobActivationResult().addJobsItem(activatedJob));

    // when
    final ActivateJobsResponse response =
        client
            .newActivateJobsCommand()
            .jobType("payment")
            .maxJobsToActivate(10)
            .workerName("paymentWorker")
            .timeout(Duration.ofMillis(1000))
            .execute();

    // then
    assertThat(response.getJobs())
        .singleElement()
        .extracting(io.camunda.client.api.response.ActivatedJob::getUserTask)
        .satisfies(testCase.assertions);
  }

  @Test
  void shouldSetTimeoutFromDuration() {
    // given
    final Duration timeout = Duration.ofMinutes(2);
    gatewayService.onActivateJobsRequest(new JobActivationResult());

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
    gatewayService.onActivateJobsRequest(new JobActivationResult());

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
    gatewayService.onActivateJobsRequest(new JobActivationResult());

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
    // given
    gatewayService.onActivateJobsRequest(new JobActivationResult());

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
    gatewayService.onActivateJobsRequest(new JobActivationResult());

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
            new CamundaObjectMapper(),
            new ActivatedJobResult().customHeaders(new HashMap<>()).variables(variables));

    // when
    final VariablesPojo variablesPojo = activatedJob.getVariablesAsType(VariablesPojo.class);

    // then
    assertThat(variablesPojo.getA()).isEqualTo(1);
  }

  @Test
  void shouldAllowSpecifyingTenantIdsAsList() {
    // given
    gatewayService.onActivateJobsRequest(new JobActivationResult());
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
    gatewayService.onActivateJobsRequest(new JobActivationResult());
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
    gatewayService.onActivateJobsRequest(new JobActivationResult());
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
    gatewayService.onActivateJobsRequest(new JobActivationResult());
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

    final ActivatedJobResult activatedJob1 = new ActivatedJobResult().variables(variablesJob1);
    final ActivatedJobResult activatedJob2 = new ActivatedJobResult().variables(variablesJob2);

    gatewayService.onActivateJobsRequest(
        new JobActivationResult().addJobsItem(activatedJob1).addJobsItem(activatedJob2));

    // when
    final ActivateJobsResponse response =
        client.newActivateJobsCommand().jobType("foo").maxJobsToActivate(3).send().join();

    assertThat(response.getJobs()).hasSize(2);

    final io.camunda.client.api.response.ActivatedJob job1 = response.getJobs().get(0);
    assertThat(job1.getVariable("key")).isEqualTo("val");
    assertThat(job1.getVariable("foo")).isEqualTo("bar");
    assertThat(job1.getVariable("joe")).isEqualTo("doe");

    final io.camunda.client.api.response.ActivatedJob job2 = response.getJobs().get(1);
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
    final ActivatedJobResult activatedJob1 = new ActivatedJobResult().variables(variables);

    gatewayService.onActivateJobsRequest(new JobActivationResult().addJobsItem(activatedJob1));

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
    final ActivatedJobResult activatedJob1 = new ActivatedJobResult().variables(variables);

    gatewayService.onActivateJobsRequest(new JobActivationResult().addJobsItem(activatedJob1));

    // when
    final ActivateJobsResponse response =
        client.newActivateJobsCommand().jobType("foo").maxJobsToActivate(3).send().join();

    assertThat(response.getJobs()).hasSize(1);

    assertThat(response.getJobs().get(0).getVariable("key")).isNull();
  }

  @Test
  void shouldSetDefaultWorkerNameWhenMissingInCommand() {
    // given
    gatewayService.onActivateJobsRequest(new JobActivationResult());

    // when
    client.newActivateJobsCommand().jobType("foo").maxJobsToActivate(1).send().join();

    // then
    final JobActivationRequest request = gatewayService.getLastRequest(JobActivationRequest.class);
    assertThat(request.getWorker()).isEqualTo(CamundaClientBuilderImpl.DEFAULT_JOB_WORKER_NAME_VAR);
  }

  private static Stream<ActivateJobWithUserTaskPropsTestCase> userTaskPropertiesProvider() {
    return Stream.of(
        new ActivateJobWithUserTaskPropsTestCase(
            "should activate job with all user task properties set",
            new UserTaskProperties()
                .action("update")
                .assignee("tony")
                .addCandidateGroupsItem("assistants")
                .addCandidateUsersItem("jarvis")
                .addCandidateUsersItem("friday")
                .addChangedAttributesItem("assignee")
                .dueDate("2019-04-22T00:00:00Z")
                .followUpDate("2018-04-23T00:00:00Z")
                .formKey("1")
                .priority(10)
                .userTaskKey("123"),
            props -> {
              assertThat(props.getAction()).isEqualTo("update");
              assertThat(props.getAssignee()).isEqualTo("tony");
              assertThat(props.getCandidateGroups()).containsExactly("assistants");
              assertThat(props.getCandidateUsers()).containsExactly("jarvis", "friday");
              assertThat(props.getChangedAttributes()).containsExactly("assignee");
              assertThat(props.getDueDate()).isEqualTo("2019-04-22T00:00:00Z");
              assertThat(props.getFollowUpDate()).isEqualTo("2018-04-23T00:00:00Z");
              assertThat(props.getFormKey()).isEqualTo(1);
              assertThat(props.getPriority()).isEqualTo(10);
              assertThat(props.getUserTaskKey()).isEqualTo(123);
            }),
        // regression test for https://github.com/camunda/camunda/issues/39474
        // DateTimeFormatter.ISO_ZONED_DATE_TIME format
        new ActivateJobWithUserTaskPropsTestCase(
            "should activate job with zoned ISO 8601 date time (FEEL expression format)",
            new UserTaskProperties()
                .dueDate("2025-12-03T12:00Z[GMT]")
                .followUpDate("2025-12-04T12:00+01:00[Europe/Paris]"),
            props -> {
              assertThat(props.getDueDate()).isEqualTo("2025-12-03T12:00:00Z");
              assertThat(props.getFollowUpDate()).isEqualTo("2025-12-04T12:00:00+01:00");
            }),
        new ActivateJobWithUserTaskPropsTestCase(
            "should activate job with empty user task properties",
            new UserTaskProperties(),
            props -> {
              assertThat(props.getAction()).isNull();
              assertThat(props.getAssignee()).isNull();
              assertThat(props.getCandidateGroups()).isEmpty();
              assertThat(props.getCandidateUsers()).isEmpty();
              assertThat(props.getChangedAttributes()).isEmpty();
              assertThat(props.getDueDate()).isNull();
              assertThat(props.getFollowUpDate()).isNull();
              assertThat(props.getFormKey()).isNull();
              assertThat(props.getPriority()).isNull();
              assertThat(props.getUserTaskKey()).isNull();
            }),
        new ActivateJobWithUserTaskPropsTestCase(
            "should activate job with null user task properties",
            null,
            props -> assertThat(props).isNull()));
  }

  @Test
  void shouldSetTenantFilterToProvidedAndIncludeTenantIds() {
    // given
    gatewayService.onActivateJobsRequest(new JobActivationResult());

    // when
    client
        .newActivateJobsCommand()
        .jobType("foo")
        .maxJobsToActivate(3)
        .tenantIds("tenant1", "tenant2")
        .tenantFilter(TenantFilter.PROVIDED)
        .send()
        .join();

    // then
    final JobActivationRequest request = gatewayService.getLastRequest(JobActivationRequest.class);
    assertThat(request.getTenantFilter()).isEqualTo(TenantFilterEnum.PROVIDED);
    assertThat(request.getTenantIds()).containsExactlyInAnyOrder("tenant1", "tenant2");
  }

  @Test
  void shouldSetTenantFilterToAssignedAndExcludeTenantIds() {
    // given
    gatewayService.onActivateJobsRequest(new JobActivationResult());

    // when
    client
        .newActivateJobsCommand()
        .jobType("foo")
        .maxJobsToActivate(3)
        .tenantIds("tenant1", "tenant2")
        .tenantFilter(TenantFilter.ASSIGNED)
        .send()
        .join();

    // then
    final JobActivationRequest request = gatewayService.getLastRequest(JobActivationRequest.class);
    assertThat(request.getTenantFilter()).isEqualTo(TenantFilterEnum.ASSIGNED);
    assertThat(request.getTenantIds()).isEmpty();
  }

  @Test
  void shouldDefaultTenantFilterToProvided() {
    // given
    gatewayService.onActivateJobsRequest(new JobActivationResult());

    // when
    client.newActivateJobsCommand().jobType("foo").maxJobsToActivate(3).send().join();

    // then
    final JobActivationRequest request = gatewayService.getLastRequest(JobActivationRequest.class);
    assertThat(request.getTenantFilter()).isEqualTo(TenantFilterEnum.PROVIDED);
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

  private static final class ActivateJobWithUserTaskPropsTestCase {

    public final String testDescription;
    public final UserTaskProperties providedProperties;
    public final Consumer<io.camunda.client.api.response.UserTaskProperties> assertions;

    public ActivateJobWithUserTaskPropsTestCase(
        final String testDescription,
        final UserTaskProperties providedProperties,
        final Consumer<io.camunda.client.api.response.UserTaskProperties> assertions) {
      this.testDescription = testDescription;
      this.providedProperties = providedProperties;
      this.assertions = assertions;
    }

    @Override
    public String toString() {
      return testDescription;
    }
  }
}

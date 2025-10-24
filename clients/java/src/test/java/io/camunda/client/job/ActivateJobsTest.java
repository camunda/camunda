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
package io.camunda.client.job;

import static io.camunda.client.TestUtil.getBytes;
import static io.camunda.client.util.JsonUtil.fromJsonAsMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.api.command.ActivateJobsCommandStep1.ActivateJobsCommandStep3;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.response.ActivateJobsResponse;
import io.camunda.client.api.response.DocumentReferenceResponse;
import io.camunda.client.impl.CamundaClientBuilderImpl;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.client.impl.response.ActivatedJobImpl;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.util.ClientTest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivatedJob;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivatedJob.JobKind;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivatedJob.ListenerEventType;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.UserTaskProperties;
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
            .setTenantId("test-tenant-1")
            .setKind(JobKind.BPMN_ELEMENT)
            .setListenerEventType(ListenerEventType.START)
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
            .setTenantId("test-tenant-2")
            .setKind(JobKind.TASK_LISTENER)
            .setListenerEventType(ListenerEventType.END)
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
            .tenantIds("test-tenant-1", "test-tenant-2")
            .send()
            .join();

    // then
    assertThat(response.getJobs()).hasSize(2);

    io.camunda.client.api.response.ActivatedJob job = response.getJobs().get(0);
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
    assertThat(job.getUserTask()).isNull();
    assertThat(job.getTenantId()).isEqualTo(activatedJob1.getTenantId());
    assertThat(job.getKind())
        .isEqualTo(
            EnumUtil.convert(
                activatedJob1.getKind(), io.camunda.client.api.search.enums.JobKind.class));
    assertThat(job.getListenerEventType())
        .isEqualTo(
            EnumUtil.convert(
                activatedJob1.getListenerEventType(),
                io.camunda.client.api.search.enums.ListenerEventType.class));

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
    assertThat(job.getUserTask()).isNull();
    assertThat(job.getTenantId()).isEqualTo(activatedJob2.getTenantId());
    assertThat(job.getKind())
        .isEqualTo(
            EnumUtil.convert(
                activatedJob2.getKind(), io.camunda.client.api.search.enums.JobKind.class));
    assertThat(job.getListenerEventType())
        .isEqualTo(
            EnumUtil.convert(
                activatedJob2.getListenerEventType(),
                io.camunda.client.api.search.enums.ListenerEventType.class));

    final ActivateJobsRequest request = gatewayService.getLastRequest();
    assertThat(request.getType()).isEqualTo("foo");
    assertThat(request.getMaxJobsToActivate()).isEqualTo(3);
    assertThat(request.getTimeout()).isEqualTo(1000);
    assertThat(request.getWorker()).isEqualTo("worker1");
  }

  @Test
  public void shouldActivateJobsWithUserTaskProperties() {
    // given
    final ActivatedJob activatedJob1 =
        ActivatedJob.newBuilder()
            // with all user task properties set
            .setUserTask(
                UserTaskProperties.newBuilder()
                    .setAction("update")
                    .setAssignee("tony")
                    .addCandidateGroups("assistants")
                    .addCandidateUsers("jarvis")
                    .addCandidateUsers("friday")
                    .addChangedAttributes("assignee")
                    .setDueDate("2019-04-22T00:00:00Z")
                    .setFollowUpDate("2018-04-23T00:00:00Z")
                    .setFormKey(123)
                    .setPriority(10)
                    .setUserTaskKey(456)
                    .build())
            .build();

    final ActivatedJob activatedJob2 =
        ActivatedJob.newBuilder()
            // with empty user task properties
            .setUserTask(UserTaskProperties.newBuilder().build())
            .build();

    final ActivatedJob activatedJob3 =
        ActivatedJob.newBuilder()
            // with not set user task properties
            .build();

    gatewayService.onActivateJobsRequest(activatedJob1, activatedJob2, activatedJob3);

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
    assertThat(response.getJobs()).hasSize(3);

    io.camunda.client.api.response.ActivatedJob job = response.getJobs().get(0);
    assertThat(job.getUserTask())
        .describedAs("Should activate job with all user task properties set")
        .satisfies(
            props -> {
              assertThat(props.getAction()).isEqualTo("update");
              assertThat(props.getAssignee()).isEqualTo("tony");
              assertThat(props.getCandidateGroups()).containsExactly("assistants");
              assertThat(props.getCandidateUsers()).containsExactly("jarvis", "friday");
              assertThat(props.getChangedAttributes()).containsExactly("assignee");
              assertThat(props.getDueDate()).isEqualTo("2019-04-22T00:00:00Z");
              assertThat(props.getFollowUpDate()).isEqualTo("2018-04-23T00:00:00Z");
              assertThat(props.getFormKey()).isEqualTo(123);
              assertThat(props.getPriority()).isEqualTo(10);
              assertThat(props.getUserTaskKey()).isEqualTo(456);
            });

    job = response.getJobs().get(1);
    assertThat(job.getUserTask())
        .describedAs("Should activate job with empty user task properties")
        .satisfies(
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
            });

    job = response.getJobs().get(2);
    assertThat(job.getUserTask())
        .describedAs("Should activate job with null user task properties")
        .isNull();
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
            new CamundaObjectMapper(),
            ActivatedJob.newBuilder()
                .setCustomHeaders("{}")
                .setVariables("{\"a\": 1, \"b\": 2}")
                .build());

    // when
    final VariablesPojo variablesPojo = activatedJob.getVariablesAsType(VariablesPojo.class);

    // then
    assertThat(variablesPojo.getA()).isEqualTo(1);
  }

  @Test
  public void shouldAllowSpecifyingTenantIdsAsList() {
    // given
    client
        .newActivateJobsCommand()
        .jobType("foo")
        .maxJobsToActivate(3)
        .tenantIds(Arrays.asList("tenant1", "tenant2"))
        .send()
        .join();

    // when
    final ActivateJobsRequest request = gatewayService.getLastRequest();

    // then
    assertThat(request.getTenantIdsList()).containsExactlyInAnyOrder("tenant1", "tenant2");
  }

  @Test
  public void shouldAllowSpecifyingTenantIdsAsVarArgs() {
    // given
    client
        .newActivateJobsCommand()
        .jobType("foo")
        .maxJobsToActivate(3)
        .tenantIds("tenant1", "tenant2")
        .send()
        .join();

    // when
    final ActivateJobsRequest request = gatewayService.getLastRequest();

    // then
    assertThat(request.getTenantIdsList()).containsExactlyInAnyOrder("tenant1", "tenant2");
  }

  @Test
  public void shouldAllowSpecifyingTenantIds() {
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
    final ActivateJobsRequest request = gatewayService.getLastRequest();

    // then
    assertThat(request.getTenantIdsList()).containsExactlyInAnyOrder("tenant1", "tenant2");
  }

  // Regression: https://github.com/camunda/camunda/issues/17513
  @Test
  public void shouldNotAccumulateTenantsOnSuccessiveOpen() {
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
    final ActivateJobsRequest request = gatewayService.getLastRequest();

    // then
    assertThat(request.getTenantIdsList()).containsExactlyInAnyOrder("tenant1", "tenant2");
  }

  @Test
  public void shouldGetSingleVariable() {
    final String variablesJob1 = "{\"key\" : \"val\", \"foo\" : \"bar\", \"joe\" : \"doe\"}";
    final String variablesJob2 = "{\"key\" : \"val2\", \"foo\" : \"bar2\", \"joe\" : \"doe2\"}";
    // given
    final ActivatedJob activatedJob1 =
        ActivatedJob.newBuilder().setVariables(variablesJob1).build();

    final ActivatedJob activatedJob2 =
        ActivatedJob.newBuilder().setVariables(variablesJob2).build();

    gatewayService.onActivateJobsRequest(activatedJob1, activatedJob2);

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
  public void shouldThrowAnErrorIfVariableNameIsNotPresent() {
    final String variables = "{\"key\" : \"val\", \"foo\" : \"bar\", \"joe\" : \"doe\"}";
    // given
    final ActivatedJob activatedJob1 = ActivatedJob.newBuilder().setVariables(variables).build();

    gatewayService.onActivateJobsRequest(activatedJob1);

    // when
    final ActivateJobsResponse response =
        client.newActivateJobsCommand().jobType("foo").maxJobsToActivate(3).send().join();

    assertThat(response.getJobs()).hasSize(1);

    final io.camunda.client.api.response.ActivatedJob job1 = response.getJobs().get(0);
    assertThatThrownBy(() -> job1.getVariable("notPresentName"))
        .isInstanceOf(ClientException.class);
  }

  @Test
  public void shouldReturnNullIfVariableValueIsNull() {
    final String variables = "{\"key\" : null}";
    // given
    final ActivatedJob activatedJob1 = ActivatedJob.newBuilder().setVariables(variables).build();

    gatewayService.onActivateJobsRequest(activatedJob1);

    // when
    final ActivateJobsResponse response =
        client.newActivateJobsCommand().jobType("foo").maxJobsToActivate(3).send().join();

    assertThat(response.getJobs()).hasSize(1);

    final io.camunda.client.api.response.ActivatedJob job1 = response.getJobs().get(0);
    assertThat(job1.getVariable("key")).isNull();
  }

  @Test
  public void shouldSetDefaultWorkerNameWhenNullPropertyIsConfigured() {
    // given
    rule.getClientBuilder().defaultJobWorkerName(null);

    // when
    client.newActivateJobsCommand().jobType("foo").maxJobsToActivate(1).send().join();

    // then
    final ActivateJobsRequest request = gatewayService.getLastRequest();
    assertThat(request.getWorker()).isEqualTo(CamundaClientBuilderImpl.DEFAULT_JOB_WORKER_NAME_VAR);
  }

  @Test
  public void shouldSetProvidedWorkerNameWhenNullPropertyIsConfigured() {
    final String workerName = "workerName";
    // given
    rule.getClientBuilder().defaultJobWorkerName(null);

    // when
    client
        .newActivateJobsCommand()
        .jobType("foo")
        .maxJobsToActivate(1)
        .workerName(workerName)
        .send()
        .join();

    // then
    final ActivateJobsRequest request = gatewayService.getLastRequest();
    assertThat(request.getWorker()).isEqualTo(workerName);
  }

  @Test
  public void shouldSetProvidedDefaultWorkerNameWhenNullPropertyIsProvidedInBuilder() {
    final String workerName = "workerName";
    // given
    rule.getClientBuilder().defaultJobWorkerName(workerName);

    // when
    client
        .newActivateJobsCommand()
        .jobType("foo")
        .maxJobsToActivate(1)
        .workerName(null)
        .send()
        .join();

    // then
    final ActivateJobsRequest request = gatewayService.getLastRequest();
    assertThat(request.getWorker()).isEqualTo(workerName);
  }

  @Test
  public void shouldParseDocumentReference() {
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
            .setVariables(
                "{\"documentReference\": ["
                    + new String(getBytes("/document/test-document-reference.json"))
                    + "]}")
            .setTenantId("test-tenant-1")
            .build();

    gatewayService.onActivateJobsRequest(activatedJob1);

    // when
    final ActivateJobsResponse response =
        client.newActivateJobsCommand().jobType("foo").maxJobsToActivate(1).send().join();
    // then
    assertThat(response.getJobs()).hasSize(1);
    final io.camunda.client.api.response.ActivatedJob job = response.getJobs().get(0);
    assertThat(job.getDocumentReferences("documentReference")).isNotNull();
    final List<DocumentReferenceResponse> documentReference =
        job.getDocumentReferences("documentReference");
    assertThat(documentReference).hasSize(1);
    final DocumentReferenceResponse documentReferenceResponse = documentReference.get(0);
    assertThat(documentReferenceResponse.getDocumentType()).isEqualTo("camunda");
    assertThat(documentReferenceResponse.getDocumentId()).isEqualTo("document-id");
    assertThat(documentReferenceResponse.getContentHash()).isEqualTo("content-hash");
    assertThat(documentReferenceResponse.getMetadata()).isNotNull();
    assertThat(documentReferenceResponse.getMetadata().getContentType()).isEqualTo("content-type");
    assertThat(documentReferenceResponse.getMetadata().getFileName()).isEqualTo("file-name");
    assertThat(documentReferenceResponse.getMetadata().getExpiresAt())
        .isEqualTo("2025-06-28T07:32:28.93912+02:00");
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

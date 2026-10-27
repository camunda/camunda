/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client.command;

import static io.camunda.zeebe.test.util.TestUtil.waitUntil;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.client.api.response.ActivateJobsResponse;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.ProcessInstanceResult;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.collection.Maps;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@ZeebeIntegration
public final class CreateProcessInstanceWithResultTest {

  @TestZeebe
  private static final TestStandaloneBroker ZEEBE =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  @AutoClose CamundaClient client;
  ZeebeResourcesHelper resourcesHelper;
  private String processId;
  private long processDefinitionKey;
  private String jobType;

  @BeforeEach
  public void init() {
    client = ZEEBE.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
    resourcesHelper = new ZeebeResourcesHelper(client);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldCreateProcessInstanceAwaitResults(
      final boolean useRest, final TestInfo testInfo) {
    deployProcesses(testInfo);
    final Map<String, Object> variables = Maps.of(entry("foo", "bar"));
    final ProcessInstanceResult result =
        createProcessInstanceWithVariables(variables, useRest).join();

    assertThat(result.getBpmnProcessId()).isEqualTo(processId);
    assertThat(result.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(result.getVariablesAsMap()).containsExactly(entry("foo", "bar"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldCreateProcessInstanceAwaitResultsWithNoVariables(
      final boolean useRest, final TestInfo testInfo) {
    // given
    deployProcesses(testInfo);
    final ProcessInstanceResult result =
        getCommand(client, useRest)
            .processDefinitionKey(processDefinitionKey)
            .withResult()
            .send()
            .join();

    // then
    assertThat(result.getBpmnProcessId()).isEqualTo(processId);
    assertThat(result.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(result.getVariablesAsMap()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldCollectMergedVariables(final boolean useRest, final TestInfo testInfo) {
    // given
    deployProcesses(testInfo);
    deployProcessWithJob();
    final Map<String, Object> variables = Maps.of(entry("foo", "bar"));
    final CamundaFuture<ProcessInstanceResult> resultFuture =
        createProcessInstanceWithVariables(variables, useRest);

    completeJobWithVariables(Map.of("x", "y"));

    // then
    final ProcessInstanceResult result = resultFuture.join();
    assertThat(result.getBpmnProcessId()).isEqualTo(processId);
    assertThat(result.getVariablesAsMap())
        .containsExactlyInAnyOrderEntriesOf(Map.of("foo", "bar", "x", "y"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldOnlyReturnVariablesInRootScope(final boolean useRest, final TestInfo testInfo) {
    // given
    deployProcesses(testInfo);
    final BpmnModelInstance processWithVariableScopes =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .subProcess(
                "sub",
                b -> {
                  b.embeddedSubProcess()
                      .startEvent()
                      .serviceTask("task", t -> t.zeebeJobType(jobType))
                      .endEvent();
                  b.zeebeInputExpression("x", "y");
                })
            .endEvent()
            .done();
    processDefinitionKey = resourcesHelper.deployProcess(processWithVariableScopes);

    final CamundaFuture<ProcessInstanceResult> resultFuture =
        createProcessInstanceWithVariables(Map.of("x", "1"), useRest);

    // when
    completeJobWithVariables(Map.of("y", "2"));

    // then
    final ProcessInstanceResult result = resultFuture.join();
    assertThat(result.getBpmnProcessId()).isEqualTo(processId);
    assertThat(result.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(result.getVariablesAsMap()).containsExactly(entry("x", "1"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldReceiveRejectionCreateProcessInstanceAwaitResults(final boolean useRest) {
    final var command = getCommand(client, useRest).processDefinitionKey(123L).withResult().send();

    assertThatThrownBy(command::join)
        .hasMessageContaining("Expected to find process definition with key '123', but none found");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldCreateProcessInstanceAwaitResultsWithFetchVariables(
      final boolean useRest, final TestInfo testInfo) {
    deployProcesses(testInfo);
    // when
    final Map<String, Object> variables = Map.of("x", "foo", "y", "bar");
    final ProcessInstanceResult result =
        getCommand(client, useRest)
            .processDefinitionKey(processDefinitionKey)
            .variables(variables)
            .withResult()
            .fetchVariables("y")
            .send()
            .join();

    // then
    assertThat(result.getBpmnProcessId()).isEqualTo(processId);
    assertThat(result.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(result.getVariablesAsMap()).containsExactly(entry("y", "bar"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldCreateProcessInstanceAwaitResultsWithTags(
      final boolean useRest, final TestInfo testInfo) {
    deployProcesses(testInfo);
    // when
    final ProcessInstanceResult result =
        getCommand(client, useRest)
            .processDefinitionKey(processDefinitionKey)
            .tags("tag1", "tag2")
            .withResult()
            .send()
            .join();

    // then
    assertThat(result.getBpmnProcessId()).isEqualTo(processId);
    assertThat(result.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(result.getTags()).isEqualTo(Set.of("tag1", "tag2"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldRespondResultWhenCompletedByPublishedMessage(
      final boolean useRest, final TestInfo testInfo) {
    // given
    deployProcesses(testInfo);
    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent()
                .message(message -> message.name("a").zeebeCorrelationKeyExpression("key"))
                .endEvent()
                .done(),
            "process.bpmn")
        .send()
        .join();

    final CamundaFuture<ProcessInstanceResult> processInstanceResult =
        getCommand(client, useRest)
            .bpmnProcessId(processId)
            .latestVersion()
            .variables(Map.of("key", "key-1"))
            .withResult()
            .send();

    // when
    client
        .newPublishMessageCommand()
        .messageName("a")
        .correlationKey("key-1")
        .variables(Map.of("message", "correlated"))
        .send()
        .join();

    // then
    assertThat(processInstanceResult.join().getVariablesAsMap())
        .containsEntry("message", "correlated");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldRespondResultWhenCompletedByCompletedJob(
      final boolean useRest, final TestInfo testInfo) {
    // given
    deployProcesses(testInfo);
    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("task"))
                .endEvent()
                .done(),
            "process.bpmn")
        .send()
        .join();

    final CamundaFuture<ProcessInstanceResult> processInstanceResult =
        getCommand(client, useRest).bpmnProcessId(processId).latestVersion().withResult().send();

    final List<ActivatedJob> jobs =
        client
            .newActivateJobsCommand()
            .jobType("task")
            .maxJobsToActivate(1)
            .send()
            .join()
            .getJobs();
    assertThat(jobs).hasSize(1);

    // when
    jobs.forEach(
        job -> client.newCompleteCommand(job).variables(Map.of("job", "completed")).send().join());

    // then
    assertThat(processInstanceResult.join().getVariablesAsMap()).containsEntry("job", "completed");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldRespondResultWhenCompletedByThrownError(
      final boolean useRest, final TestInfo testInfo) {
    // given
    deployProcesses(testInfo);
    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("task"))
                .boundaryEvent("error", e -> e.errorEventDefinition().error("error"))
                .zeebeOutputExpression("error", "error") // error variables are not propagated
                .endEvent()
                .moveToActivity("task")
                .endEvent()
                .done(),
            "process.bpmn")
        .send()
        .join();

    final CamundaFuture<ProcessInstanceResult> processInstanceResult =
        getCommand(client, useRest).bpmnProcessId(processId).latestVersion().withResult().send();

    final List<ActivatedJob> jobs =
        client
            .newActivateJobsCommand()
            .jobType("task")
            .maxJobsToActivate(1)
            .send()
            .join()
            .getJobs();
    assertThat(jobs).hasSize(1);

    // when
    jobs.forEach(
        job ->
            client
                .newThrowErrorCommand(job)
                .errorCode("error")
                .errorMessage("throwing an error")
                .variables(Map.of("error", "thrown"))
                .send()
                .join());

    // then
    assertThat(processInstanceResult.join().getVariablesAsMap()).containsEntry("error", "thrown");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldRespondResultWhenCompletedByResolvedIncident(
      final boolean useRest, final TestInfo testInfo) {
    // given
    deployProcesses(testInfo);
    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(processId)
                .startEvent("start_event_with_output_mapping")
                .exclusiveGateway("gateway")
                .sequenceFlowId("to-a")
                .conditionExpression("x > 10")
                .endEvent("a")
                .moveToLastExclusiveGateway()
                .sequenceFlowId("to-b")
                .defaultFlow()
                .endEvent("b")
                .done(),
            "process.bpmn")
        .send()
        .join();

    final CamundaFuture<ProcessInstanceResult> processInstanceResult =
        getCommand(client, useRest).bpmnProcessId(processId).latestVersion().withResult().send();

    final var incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withElementId("gateway")
            .getFirst();

    // when
    client
        .newSetVariablesCommand(incident.getValue().getElementInstanceKey())
        .variables(Map.of("x", 21))
        .send()
        .join();
    client.newResolveIncidentCommand(incident.getKey()).send().join();

    // then
    assertThat(processInstanceResult.join().getVariablesAsMap()).containsEntry("x", 21);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldRespondResultWhenCompletedByModifiedProcessInstance(
      final boolean useRest, final TestInfo testInfo) {
    // given
    deployProcesses(testInfo);
    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("task"))
                .endEvent("end_event")
                .done(),
            "process.bpmn")
        .send()
        .join();

    final CamundaFuture<ProcessInstanceResult> processInstanceResult =
        getCommand(client, useRest).bpmnProcessId(processId).latestVersion().withResult().send();

    final List<ActivatedJob> jobs =
        client
            .newActivateJobsCommand()
            .jobType("task")
            .maxJobsToActivate(1)
            .send()
            .join()
            .getJobs();
    assertThat(jobs).hasSize(1);

    // when
    jobs.forEach(
        job ->
            client
                .newModifyProcessInstanceCommand(job.getProcessInstanceKey())
                .terminateElement(job.getElementInstanceKey())
                .and()
                .activateElement("end_event")
                .withVariables(Map.of("process instance", "modified"))
                .send()
                .join());

    // then
    assertThat(processInstanceResult.join().getVariablesAsMap())
        .containsEntry("process instance", "modified");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldCreateProcessInstanceAndGetSingleVariable(
      final boolean useRest, final TestInfo testInfo) {
    deployProcesses(testInfo);
    final Map<String, Object> variables = Maps.of(entry("foo", "bar"), entry("key", "value"));
    final ProcessInstanceResult result =
        createProcessInstanceWithVariables(variables, useRest).join();

    assertThat(result.getVariable("foo")).isEqualTo("bar");
    assertThat(result.getVariable("key")).isEqualTo("value");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldCreateProcessInstanceAndGetSingleVariableWithNullValue(
      final boolean useRest, final TestInfo testInfo) {
    deployProcesses(testInfo);
    final Map<String, Object> variables = new HashMap<>();
    variables.put("key", null);
    final ProcessInstanceResult result =
        createProcessInstanceWithVariables(variables, useRest).join();

    assertThat(result.getVariable("key")).isNull();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldCreateProcessInstanceAndThrowAnErrorIfVariableIsNotPresent(
      final boolean useRest, final TestInfo testInfo) {
    deployProcesses(testInfo);
    final Map<String, Object> variables = new HashMap<>();
    variables.put("key", "value");
    final ProcessInstanceResult result =
        createProcessInstanceWithVariables(variables, useRest).join();

    assertThatThrownBy(() -> result.getVariable("notPresentKey"))
        .isInstanceOf(ClientException.class);
  }

  private CamundaFuture<ProcessInstanceResult> createProcessInstanceWithVariables(
      final Map<String, Object> variables, final boolean useRest) {
    return getCommand(client, useRest)
        .processDefinitionKey(processDefinitionKey)
        .variables(variables)
        .withResult()
        .send();
  }

  private void deployProcessWithJob() {
    processId = "processWithJob";
    processDefinitionKey =
        resourcesHelper.deployProcess(
            Bpmn.createExecutableProcess(processId)
                .startEvent("v1")
                .serviceTask(
                    "task",
                    t -> {
                      t.zeebeJobType(jobType);
                    })
                .endEvent("end")
                .done());
  }

  private void completeJobWithVariables(final Map<String, Object> variables) {
    waitUntil(() -> RecordingExporter.jobRecords(JobIntent.CREATED).withType(jobType).exists());

    final ActivateJobsResponse response =
        client.newActivateJobsCommand().jobType(jobType).maxJobsToActivate(1).send().join();

    // when
    client
        .newCompleteCommand(response.getJobs().iterator().next().getKey())
        .variables(variables)
        .send();
  }

  private CreateProcessInstanceCommandStep1 getCommand(
      final CamundaClient client, final boolean useRest) {
    final CreateProcessInstanceCommandStep1 createInstanceCommand =
        client.newCreateInstanceCommand();
    return useRest ? createInstanceCommand.useRest() : createInstanceCommand.useGrpc();
  }

  private void deployProcesses(final TestInfo testInfo) {
    processId = "process-" + testInfo.getTestMethod().get().getName();
    processDefinitionKey =
        resourcesHelper.deployProcess(
            Bpmn.createExecutableProcess(processId).startEvent("v1").done());
    jobType = "job-" + testInfo.getTestMethod().get().getName();
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AgentDefinitionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.AgentDefinitionType;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;

/**
 * Covers the core detection-to-emission story for {@code AgentDefinition:CREATED} at deploy time:
 * an element carrying an explicit {@code zeebe:agentDefinition} marker creates exactly one {@code
 * AgentDefinition} per deployed process version, while an unmarked element creates nothing. Also
 * covers the deployment versioning invariants also enforced for other deployed resources: a fully
 * duplicate deployment must not create an additional {@code AgentDefinition}, while a mixed
 * deployment that reassigns a duplicate BPMN resource to a new process version must create a new
 * one for it.
 *
 * <p>Also covers a single BPMN resource containing more than one executable, agent-marked {@code
 * <process>} element, pinning down that {@code BpmnResourceTransformer} always finds the right
 * {@code ExecutableProcess} to scan for agent markers, for every process it emits metadata for.
 */
public final class AgentDefinitionDeploymentTest {

  private static final String FORM_V1 = "/form/test-form-1.form";
  private static final String FORM_V2 = "/form/test-form-1_v2.form";

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateAgentDefinitionForElementWithExplicitMarker() {
    // given
    final var processId = "process-explicit-marker";
    final var elementId = "agent-sub-process";

    // when
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .adHocSubProcess(
                        elementId,
                        ahsp ->
                            ahsp.name("AI Agent")
                                .zeebeJobType("agent-sub-process-job")
                                .zeebeAiAgentSubProcessDefinition()
                                .task("inner"))
                    .endEvent()
                    .done())
            .deploy();
    final var processDefinitionKey =
        deployment.getValue().getProcessesMetadata().getFirst().getProcessDefinitionKey();

    // then
    final var agentDefinitionRecord =
        RecordingExporter.agentDefinitionRecords(AgentDefinitionIntent.CREATED)
            .withProcessDefinitionKey(processDefinitionKey)
            .getFirst();

    Assertions.assertThat(agentDefinitionRecord)
        .describedAs(
            "Should key the AgentDefinition:CREATED record with its own agentDefinitionKey")
        .hasKey(agentDefinitionRecord.getValue().getAgentDefinitionKey());

    Assertions.assertThat(agentDefinitionRecord.getValue())
        .describedAs("Should populate every field of the AgentDefinition from the explicit marker")
        .hasAgentType(AgentDefinitionType.AI_AGENT_SUB_PROCESS)
        .hasName("AI Agent")
        .hasElementId(elementId)
        .hasBpmnProcessId(processId)
        .hasProcessDefinitionKey(processDefinitionKey)
        .hasProcessDefinitionVersion(1)
        .hasProcessDefinitionVersionTag("")
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    assertAgentDefinitionCreatedAfterProcessCreated(deployment.getKey());
  }

  @Test
  public void shouldNotCreateAgentDefinitionForElementWithoutAgentMarker() {
    // given — a plain service task, an ad-hoc sub-process with no marker, and a service task with
    // a modelerTemplate but no explicit marker: none of these are agent definitions, since a
    // modelerTemplate alone no longer resolves to an agent type
    final var processId = "process-unmarked";

    // when
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask("plain-task", t -> t.zeebeJobType("plain-task-job"))
                    .serviceTask(
                        "templated-task",
                        t ->
                            t.zeebeJobType("templated-task-job")
                                .zeebeModelerTemplate("some.connector.template.v3"))
                    .adHocSubProcess("plain-ad-hoc", ahsp -> ahsp.task("inner"))
                    .endEvent()
                    .done())
            .deploy();
    final var processDefinitionKey =
        deployment.getValue().getProcessesMetadata().getFirst().getProcessDefinitionKey();

    // then
    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                records ->
                    RecordingExporter.agentDefinitionRecords()
                        .withProcessDefinitionKey(processDefinitionKey)
                        .exists()))
        .describedAs("Should not create any AgentDefinition for a process without an agent marker")
        .isFalse();
  }

  @Test
  public void shouldCreateAgentDefinitionForEachMarkedProcessInMultiProcessResource() {
    // given — MULTI_PROCESS_RESOURCE: two independent, executable, agent-marked processes sharing
    // a single BPMN resource
    final var firstProcessId = "process-multi-marked-first";
    final var firstElementId = "agent-task-first";
    final var secondProcessId = "process-multi-marked-second";
    final var secondElementId = "agent-task-second";

    // when
    final var deployment =
        engine
            .deployment()
            .withXmlClasspathResource("/processes/agent-definition-multi-process.bpmn")
            .deploy();

    // then
    assertThat(deployment.getValue().getProcessesMetadata())
        .describedAs("Should deploy both executable processes contained in the same resource")
        .extracting(ProcessMetadataValue::getBpmnProcessId)
        .containsExactlyInAnyOrder(firstProcessId, secondProcessId);

    final var firstProcessDefinitionKey = processDefinitionKeyOf(deployment, firstProcessId);
    final var secondProcessDefinitionKey = processDefinitionKeyOf(deployment, secondProcessId);

    assertThat(secondProcessDefinitionKey)
        .describedAs(
            "Should assign each sibling process its own processDefinitionKey, even though both"
                + " are deployed from the same resource")
        .isNotEqualTo(firstProcessDefinitionKey);

    final var firstAgentDefinition =
        RecordingExporter.agentDefinitionRecords(AgentDefinitionIntent.CREATED)
            .withProcessDefinitionKey(firstProcessDefinitionKey)
            .getFirst();
    final var secondAgentDefinition =
        RecordingExporter.agentDefinitionRecords(AgentDefinitionIntent.CREATED)
            .withProcessDefinitionKey(secondProcessDefinitionKey)
            .getFirst();

    Assertions.assertThat(firstAgentDefinition.getValue())
        .describedAs(
            "Should create an AgentDefinition for the first sibling process's own agent-marked"
                + " element, correctly resolved among several processes sharing a resource")
        .hasElementId(firstElementId)
        .hasBpmnProcessId(firstProcessId)
        .hasProcessDefinitionKey(firstProcessDefinitionKey);

    Assertions.assertThat(secondAgentDefinition.getValue())
        .describedAs(
            "Should create an AgentDefinition for the second sibling process's own agent-marked"
                + " element, independently from the first")
        .hasElementId(secondElementId)
        .hasBpmnProcessId(secondProcessId)
        .hasProcessDefinitionKey(secondProcessDefinitionKey);

    assertThat(secondAgentDefinition.getValue().getAgentDefinitionKey())
        .describedAs(
            "Should generate a distinct agentDefinitionKey for each sibling process's"
                + " AgentDefinition")
        .isNotEqualTo(firstAgentDefinition.getValue().getAgentDefinitionKey());
  }

  @Test
  public void shouldCreateNewAgentDefinitionOnRedeploy() {
    // given
    final var processId = "process-redeploy";
    final var elementId = "agent-task";

    final var firstDeployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask(
                        elementId,
                        t -> t.zeebeJobType("agent-task-job-v1").zeebeAiAgentTaskDefinition())
                    .endEvent()
                    .done())
            .deploy();
    final var processDefinitionKeyV1 =
        firstDeployment.getValue().getProcessesMetadata().getFirst().getProcessDefinitionKey();
    final var agentDefinitionKeyV1 =
        RecordingExporter.agentDefinitionRecords(AgentDefinitionIntent.CREATED)
            .withProcessDefinitionKey(processDefinitionKeyV1)
            .getFirst()
            .getValue()
            .getAgentDefinitionKey();

    // when — redeploy with a changed resource to force a new process version
    final var secondDeployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask(
                        elementId,
                        t -> t.zeebeJobType("agent-task-job-v2").zeebeAiAgentTaskDefinition())
                    .endEvent()
                    .done())
            .deploy();
    final var processDefinitionKeyV2 =
        secondDeployment.getValue().getProcessesMetadata().getFirst().getProcessDefinitionKey();

    // then
    assertThat(processDefinitionKeyV2)
        .describedAs("Should assign a new processDefinitionKey to the redeployed process")
        .isNotEqualTo(processDefinitionKeyV1);

    final var agentDefinitionRecordV2 =
        RecordingExporter.agentDefinitionRecords(AgentDefinitionIntent.CREATED)
            .withProcessDefinitionKey(processDefinitionKeyV2)
            .getFirst();

    Assertions.assertThat(agentDefinitionRecordV2.getValue())
        .describedAs("Should create a new AgentDefinition for the new process version")
        .hasBpmnProcessId(processId)
        .hasProcessDefinitionKey(processDefinitionKeyV2)
        .hasProcessDefinitionVersion(2)
        .hasElementId(elementId)
        .describedAs(
            "Should fall back to the elementId as the AgentDefinition name when the element itself has no name")
        .hasName(elementId);

    assertThat(agentDefinitionRecordV2.getValue().getAgentDefinitionKey())
        .describedAs(
            "Should generate a distinct agentDefinitionKey for the redeployed process's"
                + " AgentDefinition")
        .isNotEqualTo(agentDefinitionKeyV1);
  }

  @Test
  public void shouldNotCreateAgentDefinitionForFullyDuplicateDeployment() {
    // given
    final var processId = "process-fully-duplicate";
    final var elementId = "agent-task";
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask(
                elementId, t -> t.zeebeJobType("agent-task-job").zeebeAiAgentTaskDefinition())
            .endEvent()
            .done();

    final var firstDeployment = engine.deployment().withXmlResource(process).deploy();
    final var processDefinitionKey =
        firstDeployment.getValue().getProcessesMetadata().getFirst().getProcessDefinitionKey();
    assertThat(
            RecordingExporter.agentDefinitionRecords(AgentDefinitionIntent.CREATED)
                .withProcessDefinitionKey(processDefinitionKey)
                .exists())
        .describedAs("Should create an AgentDefinition on the first deployment as a precondition")
        .isTrue();

    // when — redeploy the exact same, unchanged resource
    final var secondDeployment = engine.deployment().withXmlResource(process).deploy();

    // then
    assertThat(secondDeployment.getValue().getProcessesMetadata())
        .singleElement()
        .satisfies(
            metadata ->
                Assertions.assertThat(metadata)
                    .describedAs("Should keep the duplicate process at its original version")
                    .hasVersion(1)
                    .isDuplicate()
                    .hasProcessDefinitionKey(processDefinitionKey));

    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                records ->
                    RecordingExporter.agentDefinitionRecords(AgentDefinitionIntent.CREATED)
                        .withProcessDefinitionKey(processDefinitionKey)
                        .skip(1)
                        .exists()))
        .describedAs(
            "Should not create a second AgentDefinition for the duplicate process definition")
        .isFalse();
  }

  @Test
  public void shouldCreateAgentDefinitionWithReassignedKeyForMixedDeploymentRedeploy() {
    // given — a BPMN resource with an agent marker deployed alongside a form resource
    final var processId = "process-mixed-redeploy";
    final var elementId = "agent-task";
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask(
                elementId, t -> t.zeebeJobType("agent-task-job").zeebeAiAgentTaskDefinition())
            .endEvent()
            .done();

    final var firstDeployment =
        engine.deployment().withXmlResource(process).withJsonClasspathResource(FORM_V1).deploy();
    final var processDefinitionKeyV1 =
        firstDeployment.getValue().getProcessesMetadata().getFirst().getProcessDefinitionKey();
    final var agentDefinitionKeyV1 =
        RecordingExporter.agentDefinitionRecords(AgentDefinitionIntent.CREATED)
            .withProcessDefinitionKey(processDefinitionKeyV1)
            .getFirst()
            .getValue()
            .getAgentDefinitionKey();

    // when — redeploy with the byte-identical BPMN resource but a changed form resource, forcing
    // the deployment as a whole to reassign the duplicate BPMN process to a new version
    final var secondDeployment =
        engine.deployment().withXmlResource(process).withJsonClasspathResource(FORM_V2).deploy();
    final var processMetadataV2 = secondDeployment.getValue().getProcessesMetadata().getFirst();
    final var processDefinitionKeyV2 = processMetadataV2.getProcessDefinitionKey();

    // then
    Assertions.assertThat(processMetadataV2)
        .describedAs(
            "Should reassign the duplicate BPMN process to a new version because a sibling"
                + " resource in the same deployment changed")
        .hasVersion(2)
        .isNotDuplicate();

    assertThat(processDefinitionKeyV2)
        .describedAs(
            "Should assign the duplicate BPMN process a fresh processDefinitionKey, distinct"
                + " from its original one, because a sibling resource in the same deployment"
                + " changed")
        .isNotEqualTo(processDefinitionKeyV1);

    final var agentDefinitionRecordV2 =
        RecordingExporter.agentDefinitionRecords(AgentDefinitionIntent.CREATED)
            .withProcessDefinitionKey(processDefinitionKeyV2)
            .getFirst();

    Assertions.assertThat(agentDefinitionRecordV2.getValue())
        .describedAs(
            "Should create a new AgentDefinition for the reassigned process version, matching its"
                + " new processDefinitionVersion")
        .hasElementId(elementId)
        .hasBpmnProcessId(processId)
        .hasProcessDefinitionKey(processDefinitionKeyV2)
        .hasProcessDefinitionVersion(2);

    assertThat(agentDefinitionRecordV2.getValue().getAgentDefinitionKey())
        .describedAs(
            "Should generate a distinct agentDefinitionKey for the reassigned process version's"
                + " AgentDefinition")
        .isNotEqualTo(agentDefinitionKeyV1);
  }

  private static void assertAgentDefinitionCreatedAfterProcessCreated(final long deploymentKey) {
    final var processAndAgentDefinitionRecords =
        RecordingExporter.records()
            .onlyEvents()
            .limit(r -> r.getIntent() == DeploymentIntent.CREATED && r.getKey() == deploymentKey)
            .withValueTypes(ValueType.AGENT_DEFINITION, ValueType.PROCESS)
            .asList();

    assertThat(processAndAgentDefinitionRecords)
        .describedAs(
            "Should emit AgentDefinition:CREATED after ProcessIntent.CREATED, so"
                + " that a consumer sees the process an AgentDefinition references already"
                + " created by the time it observes the AgentDefinition itself")
        .extracting(Record::getValueType, Record::getIntent)
        .containsExactly(
            tuple(ValueType.PROCESS, ProcessIntent.CREATED),
            tuple(ValueType.AGENT_DEFINITION, AgentDefinitionIntent.CREATED));
  }

  private static long processDefinitionKeyOf(
      final Record<DeploymentRecordValue> deployment, final String bpmnProcessId) {
    return deployment.getValue().getProcessesMetadata().stream()
        .filter(metadata -> metadata.getBpmnProcessId().equals(bpmnProcessId))
        .findFirst()
        .orElseThrow()
        .getProcessDefinitionKey();
  }
}

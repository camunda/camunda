/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance.migration;

import static io.camunda.zeebe.engine.processing.processinstance.migration.MigrationTestUtil.extractProcessDefinitionKeyByProcessId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryMessageContent;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceDefinition;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AgentDefinitionIntent;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.value.AgentHistoryContentType;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRole;
import io.camunda.zeebe.protocol.record.value.AgentInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class MigrateAgentInstanceTest {

  private static final String AGENT_JOB_TYPE = "agent-job";

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldMigrateOrphanedButActiveAgentInstanceOfServiceTask() {
    // given — an agent instance created on a service task whose job completes (and the process
    // moves on to "B") before migration, while the agent instance itself stays active; "A" is
    // explicitly mapped to "A2" even though it no longer has an active element instance
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .versionTag("v1")
                    .startEvent()
                    .serviceTask(
                        "A", t -> t.zeebeJobType(AGENT_JOB_TYPE).zeebeAiAgentTaskDefinition())
                    .userTask("B")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .versionTag("v2")
                    .startEvent()
                    .serviceTask(
                        "A2", t -> t.zeebeJobType(AGENT_JOB_TYPE).zeebeAiAgentTaskDefinition())
                    .userTask("B2")
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    final var agentTaskInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("A")
            .getFirst();
    final long agentInstanceKey =
        engine
            .agentInstances()
            .withElementInstanceKey(agentTaskInstance.getKey())
            .create()
            .getKey();

    RecordingExporter.jobRecords(JobIntent.CREATED).withType(AGENT_JOB_TYPE).await();
    engine.jobs().withType(AGENT_JOB_TYPE).activate();
    engine.job().ofInstance(processInstanceKey).withType(AGENT_JOB_TYPE).complete();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("A")
                .exists())
        .describedAs("The owning service task has completed before migration")
        .isTrue();

    // when
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "A2")
        .addMappingInstruction("B", "B2")
        .migrate();

    // then — the agent instance is migrated even though its owning element instance "A" already
    // completed and is not part of the migrated element tree
    Assertions.assertThat(
            RecordingExporter.agentInstanceRecords(AgentInstanceIntent.MIGRATED)
                .withRecordKey(agentInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("The agent instance keeps belonging to the same process instance")
        .hasProcessInstanceKey(processInstanceKey)
        .describedAs("Definition fields are updated to the target process definition")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .hasProcessDefinitionVersionTag("v2")
        .describedAs("elementId is remapped despite \"A\" having no active element instance")
        .hasElementId("A2");
  }

  @Test
  public void shouldRejectMigrationWhenOrphanedAgentInstanceOwningElementIsRemoved() {
    // given — service task "A" completes, then a new v2 of the same process definition is deployed
    // that drops "A". The agent instance created on "A" stays active (orphaned), but "A" no longer
    // exists in v2 and is not mapped, so the orphaned instance could not keep an agent definition
    // after migration — which an agent instance must always have
    final String processId = helper.getBpmnProcessId();

    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .versionTag("v1")
                .startEvent()
                .serviceTask("A", t -> t.zeebeJobType(AGENT_JOB_TYPE).zeebeAiAgentTaskDefinition())
                .userTask("B")
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    final var agentTaskInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("A")
            .getFirst();
    engine.agentInstances().withElementInstanceKey(agentTaskInstance.getKey()).create();

    RecordingExporter.jobRecords(JobIntent.CREATED).withType(AGENT_JOB_TYPE).await();
    engine.jobs().withType(AGENT_JOB_TYPE).activate();
    engine.job().ofInstance(processInstanceKey).withType(AGENT_JOB_TYPE).complete();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("A")
                .exists())
        .describedAs("The owning service task has completed before migration")
        .isTrue();

    // deploy v2 of the same process definition, which no longer contains "A"
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .versionTag("v2")
                    .startEvent()
                    .userTask("B2")
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, processId);

    // when — "A" is not mapped: it has no active element instance and v2 has no "A" to map it to
    final var rejection =
        engine
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .migration()
            .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
            .addMappingInstruction("B", "B2")
            .expectRejection()
            .migrate();

    // then — the orphaned agent instance is validated even though "A" is not part of the migrated
    // element tree, so the migration is rejected
    Assertions.assertThat(rejection)
        .hasIntent(ProcessInstanceMigrationIntent.MIGRATE)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            String.format(
                """
                Expected to migrate process instance '%d' \
                but the agent instance with element id 'A' would be migrated to element 'A' \
                that has no agent definition. \
                An agent instance must always belong to an agent definition.""",
                processInstanceKey));
  }

  @Test
  public void shouldMigrateMultipleAgentInstancesOfServiceTasksInSameProcessInstance() {
    // given — two service tasks in parallel, each with its own agent instance, both still active
    // (neither job completes) at migration time; "A" is remapped to "A2" while "B" keeps its id
    // unchanged across the migration, requiring an explicit self-mapping since it stays active
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";
    final String otherJobType = "other-agent-job";
    final String firstSystemPrompt = "Summarize the incoming request for review.";
    final String secondSystemPrompt = "Draft a polite acknowledgement email.";

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .versionTag("v1")
                    .startEvent()
                    .parallelGateway("fork")
                    .serviceTask(
                        "A", t -> t.zeebeJobType(AGENT_JOB_TYPE).zeebeAiAgentTaskDefinition())
                    .parallelGateway("join")
                    .endEvent()
                    .moveToNode("fork")
                    .serviceTask(
                        "B", t -> t.zeebeJobType(otherJobType).zeebeAiAgentTaskDefinition())
                    .connectTo("join")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .versionTag("v2")
                    .startEvent()
                    .parallelGateway("fork2")
                    .serviceTask(
                        "A2", t -> t.zeebeJobType(AGENT_JOB_TYPE).zeebeAiAgentTaskDefinition())
                    .parallelGateway("join2")
                    .endEvent()
                    .moveToNode("fork2")
                    .serviceTask(
                        "B", t -> t.zeebeJobType(otherJobType).zeebeAiAgentTaskDefinition())
                    .connectTo("join2")
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    final var firstTaskInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("A")
            .getFirst();
    final var secondTaskInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("B")
            .getFirst();
    // set each agent instance's definition the live way: via a CONFIGURATION history item in
    // CREATE's own history batch, applied inline by AgentInstanceCreateProcessor before it
    // appends AGENT_INSTANCE:CREATED.
    engine.jobs().withType(AGENT_JOB_TYPE).activate();
    final var firstJobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(AGENT_JOB_TYPE)
            .getFirst()
            .getKey();
    final var firstConfigItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-config-a")
            .setRole(AgentHistoryRole.CONFIGURATION)
            .setLoopIteration(1);
    firstConfigItem.setModel("gpt-4o").setProvider("openai");
    firstConfigItem.addSystemPrompt(
        new AgentHistoryMessageContent()
            .setContentType(AgentHistoryContentType.TEXT)
            .setText(firstSystemPrompt));
    firstConfigItem.setChangedAttributes(List.of("model", "provider", "systemPrompt"));
    final long firstAgentInstanceKey =
        engine
            .agentInstances()
            .withElementInstanceKey(firstTaskInstance.getKey())
            .withJobKey(firstJobKey)
            .withHistory(List.of(firstConfigItem))
            .create()
            .getKey();

    engine.jobs().withType(otherJobType).activate();
    final var secondJobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(otherJobType)
            .getFirst()
            .getKey();
    final var secondConfigItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-config-b")
            .setRole(AgentHistoryRole.CONFIGURATION)
            .setLoopIteration(1);
    secondConfigItem.setModel("claude-sonnet-4-5").setProvider("anthropic");
    secondConfigItem.addSystemPrompt(
        new AgentHistoryMessageContent()
            .setContentType(AgentHistoryContentType.TEXT)
            .setText(secondSystemPrompt));
    secondConfigItem.setChangedAttributes(List.of("model", "provider", "systemPrompt"));
    final long secondAgentInstanceKey =
        engine
            .agentInstances()
            .withElementInstanceKey(secondTaskInstance.getKey())
            .withJobKey(secondJobKey)
            .withHistory(List.of(secondConfigItem))
            .create()
            .getKey();

    // when
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "A2")
        .addMappingInstruction("B", "B")
        .migrate();

    // then
    final var migratedValuesByAgentInstanceKey =
        RecordingExporter.agentInstanceRecords(AgentInstanceIntent.MIGRATED)
            .withProcessInstanceKey(processInstanceKey)
            .limit(2)
            .map(Record::getValue)
            .collect(
                Collectors.toMap(
                    AgentInstanceRecordValue::getAgentInstanceKey, Function.identity()));

    assertThat(migratedValuesByAgentInstanceKey)
        .describedAs("Both agent instances are migrated to the target process definition")
        .containsOnlyKeys(firstAgentInstanceKey, secondAgentInstanceKey);

    Assertions.assertThat(migratedValuesByAgentInstanceKey.get(firstAgentInstanceKey))
        .describedAs(
            "\"A\"'s agent instance moves to the target process definition and is remapped to \"A2\"")
        .hasProcessInstanceKey(processInstanceKey)
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .hasProcessDefinitionVersion(1)
        .hasProcessDefinitionVersionTag("v2")
        .hasElementId("A2")
        .describedAs(
            "Properties migration must not touch stay exactly as they were before migration")
        .hasDefinition(
            new AgentInstanceDefinition()
                .setModel("gpt-4o")
                .setProvider("openai")
                .setSystemPrompt(
                    List.of(
                        new AgentHistoryMessageContent()
                            .setContentType(AgentHistoryContentType.TEXT)
                            .setText(firstSystemPrompt))));

    Assertions.assertThat(migratedValuesByAgentInstanceKey.get(secondAgentInstanceKey))
        .describedAs(
            "\"B\"'s agent instance moves to the target process definition but keeps its "
                + "unchanged id")
        .hasProcessInstanceKey(processInstanceKey)
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .hasProcessDefinitionVersion(1)
        .hasProcessDefinitionVersionTag("v2")
        .hasElementId("B")
        .describedAs(
            "Properties migration must not touch stay exactly as they were before migration")
        .hasDefinition(
            new AgentInstanceDefinition()
                .setModel("claude-sonnet-4-5")
                .setProvider("anthropic")
                .setSystemPrompt(
                    List.of(
                        new AgentHistoryMessageContent()
                            .setContentType(AgentHistoryContentType.TEXT)
                            .setText(secondSystemPrompt))));
  }

  @Test
  public void shouldMigrateAgentInstanceOfAdHocSubProcess() {
    // given — an agent instance attached to an ad-hoc sub-process itself, the other element type
    // AgentInstanceCreateProcessor supports besides SERVICE_TASK
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .adHocSubProcess(
                        "ahsp",
                        ahsp -> {
                          ahsp.zeebeAiAgentSubProcessDefinition();
                          ahsp.task("tool");
                          ahsp.zeebeJobType(AGENT_JOB_TYPE);
                        })
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .adHocSubProcess(
                        "ahsp2",
                        ahsp -> {
                          ahsp.zeebeAiAgentSubProcessDefinition();
                          ahsp.task("tool2");
                          ahsp.zeebeJobType(AGENT_JOB_TYPE);
                        })
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    final long adHocSubProcessInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("ahsp")
            .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
            .getFirst()
            .getKey();
    final long agentInstanceKey =
        engine
            .agentInstances()
            .withElementInstanceKey(adHocSubProcessInstanceKey)
            .create()
            .getKey();

    // when
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("ahsp", "ahsp2")
        .migrate();

    // then
    Assertions.assertThat(
            RecordingExporter.agentInstanceRecords(AgentInstanceIntent.MIGRATED)
                .withRecordKey(agentInstanceKey)
                .getFirst()
                .getValue())
        .describedAs(
            "The agent instance attached to the ad-hoc sub-process container migrates like any "
                + "other agent instance, remapping to the target definition and element id")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("ahsp2");
  }

  @Test
  public void shouldReResolveAgentDefinitionKeyWhenElementIsRemapped() {
    // given — an agent-marked service task "A" is remapped to an agent-marked service task "A2" in
    // a different target process definition, so the target element has its own, distinct agent
    // definition created at deploy time
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask(
                        "A", t -> t.zeebeJobType(AGENT_JOB_TYPE).zeebeAiAgentTaskDefinition())
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .serviceTask(
                        "A2", t -> t.zeebeJobType(AGENT_JOB_TYPE).zeebeAiAgentTaskDefinition())
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);
    final long targetAgentDefinitionKey = agentDefinitionKey(targetProcessDefinitionKey, "A2");

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    final var agentTaskInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("A")
            .getFirst();
    final long agentInstanceKey =
        engine
            .agentInstances()
            .withElementInstanceKey(agentTaskInstance.getKey())
            .create()
            .getKey();
    final long sourceAgentDefinitionKey =
        RecordingExporter.agentInstanceRecords(AgentInstanceIntent.CREATED)
            .withRecordKey(agentInstanceKey)
            .getFirst()
            .getValue()
            .getAgentDefinitionKey();

    // when
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "A2")
        .migrate();

    // then — the agent instance points at the target element's own agent definition, not the
    // source one it was created with
    Assertions.assertThat(
            RecordingExporter.agentInstanceRecords(AgentInstanceIntent.MIGRATED)
                .withRecordKey(agentInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("elementId is remapped to the target element")
        .hasElementId("A2")
        .describedAs(
            "agentDefinitionKey is re-resolved to the remapped target element's definition")
        .hasAgentDefinitionKey(targetAgentDefinitionKey);
    assertThat(targetAgentDefinitionKey)
        .describedAs("the target agent definition differs from the source one")
        .isNotEqualTo(sourceAgentDefinitionKey);
  }

  @Test
  public void shouldReResolveAgentDefinitionKeyWhenElementKeepsItsId() {
    // given — an agent-marked service task "A" is migrated to another process definition that keeps
    // the same element id "A" (a self-mapping); the target still creates its own agent definition
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask(
                        "A", t -> t.zeebeJobType(AGENT_JOB_TYPE).zeebeAiAgentTaskDefinition())
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .serviceTask(
                        "A", t -> t.zeebeJobType(AGENT_JOB_TYPE).zeebeAiAgentTaskDefinition())
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);
    final long targetAgentDefinitionKey = agentDefinitionKey(targetProcessDefinitionKey, "A");

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    final var agentTaskInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("A")
            .getFirst();
    final long agentInstanceKey =
        engine
            .agentInstances()
            .withElementInstanceKey(agentTaskInstance.getKey())
            .create()
            .getKey();

    // when
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "A")
        .migrate();

    // then — even without an element id change, the key is re-resolved to the target definition's
    // own agent definition
    Assertions.assertThat(
            RecordingExporter.agentInstanceRecords(AgentInstanceIntent.MIGRATED)
                .withRecordKey(agentInstanceKey)
                .getFirst()
                .getValue())
        .hasElementId("A")
        .describedAs(
            "agentDefinitionKey is re-resolved to the target definition's agent definition")
        .hasAgentDefinitionKey(targetAgentDefinitionKey);
  }

  @Test
  public void shouldRejectMigratingAgentInstanceToElementWithoutAgentDefinition() {
    // given — an agent-marked service task "A" is migrated onto a plain service task "A2" that
    // carries no agent definition; an agent instance must always belong to an agent definition
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask(
                        "A", t -> t.zeebeJobType(AGENT_JOB_TYPE).zeebeAiAgentTaskDefinition())
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .serviceTask("A2", t -> t.zeebeJobType(AGENT_JOB_TYPE))
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    final var agentTaskInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("A")
            .getFirst();
    engine.agentInstances().withElementInstanceKey(agentTaskInstance.getKey()).create();

    // when
    final var rejection =
        engine
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .migration()
            .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
            .addMappingInstruction("A", "A2")
            .expectRejection()
            .migrate();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(ProcessInstanceMigrationIntent.MIGRATE)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            String.format(
                """
                Expected to migrate process instance '%d' \
                but the agent instance with element id 'A' would be migrated to element 'A2' \
                that has no agent definition. \
                An agent instance must always belong to an agent definition.""",
                processInstanceKey));
  }

  private long agentDefinitionKey(final long processDefinitionKey, final String elementId) {
    return RecordingExporter.agentDefinitionRecords(AgentDefinitionIntent.CREATED)
        .withProcessDefinitionKey(processDefinitionKey)
        .withElementId(elementId)
        .getFirst()
        .getValue()
        .getAgentDefinitionKey();
  }

  @Test
  public void shouldRejectMigrationWhenAgentDefinitionTypeChanges() {
    // given — an AI agent task "A" is mapped to an external agent task "A2"; both are service
    // tasks, so the element type is unchanged and only the agent definition type differs
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask(
                        "A", t -> t.zeebeJobType(AGENT_JOB_TYPE).zeebeAiAgentTaskDefinition())
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .serviceTask(
                        "A2", t -> t.zeebeJobType(AGENT_JOB_TYPE).zeebeExternalAgentDefinition())
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    final var agentTaskInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("A")
            .getFirst();
    // create an agent instance and leave its job running so its owning element stays active
    engine.agentInstances().withElementInstanceKey(agentTaskInstance.getKey()).create();

    // when
    final var rejection =
        engine
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .migration()
            .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
            .addMappingInstruction("A", "A2")
            .expectRejection()
            .migrate();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(ProcessInstanceMigrationIntent.MIGRATE)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            String.format(
                """
                Expected to migrate process instance '%d' \
                but the agent instance element with id 'A' has agent definition type 'AI_AGENT_TASK' \
                while the mapped target element with id 'A2' has a different agent definition \
                type 'EXTERNAL_AGENT'. \
                An agent instance's type must not change on migration.""",
                processInstanceKey));
  }

  @Test
  public void shouldRejectMigrationWhenOrphanedAgentInstanceChangesAgentDefinitionType() {
    // given — an agent instance whose owning service task "A" (AI agent) completes before migration
    // while the agent instance itself stays active (orphaned); "A" is mapped to an external agent
    // task "A2", so the agent definition type would change for an instance that is no longer part
    // of the active element tree
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask(
                        "A", t -> t.zeebeJobType(AGENT_JOB_TYPE).zeebeAiAgentTaskDefinition())
                    .userTask("B")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .serviceTask(
                        "A2", t -> t.zeebeJobType(AGENT_JOB_TYPE).zeebeExternalAgentDefinition())
                    .userTask("B2")
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    final var agentTaskInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("A")
            .getFirst();
    engine.agentInstances().withElementInstanceKey(agentTaskInstance.getKey()).create();

    // complete the agentic job so "A" completes and the process moves on to "B", orphaning the
    // still-active agent instance
    RecordingExporter.jobRecords(JobIntent.CREATED).withType(AGENT_JOB_TYPE).await();
    engine.jobs().withType(AGENT_JOB_TYPE).activate();
    engine.job().ofInstance(processInstanceKey).withType(AGENT_JOB_TYPE).complete();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("A")
                .exists())
        .describedAs("The owning service task has completed before migration")
        .isTrue();

    // when
    final var rejection =
        engine
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .migration()
            .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
            .addMappingInstruction("A", "A2")
            .addMappingInstruction("B", "B2")
            .expectRejection()
            .migrate();

    // then — the orphaned agent instance is validated even though "A" is not part of the migrated
    // element tree, so the type change is rejected
    Assertions.assertThat(rejection)
        .hasIntent(ProcessInstanceMigrationIntent.MIGRATE)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            String.format(
                """
                Expected to migrate process instance '%d' \
                but the agent instance element with id 'A' has agent definition type 'AI_AGENT_TASK' \
                while the mapped target element with id 'A2' has a different agent definition \
                type 'EXTERNAL_AGENT'. \
                An agent instance's type must not change on migration.""",
                processInstanceKey));
  }

  @Test
  public void shouldAllowMigratingElementWithoutAgentInstanceToAgentElement() {
    // given — a plain service task "A" (no agent definition, no agent instance) is mapped to an AI
    // agent task "A2". With no agent instance to preserve, the migration is allowed
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask("A", t -> t.zeebeJobType(AGENT_JOB_TYPE))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .serviceTask(
                        "A2", t -> t.zeebeJobType(AGENT_JOB_TYPE).zeebeAiAgentTaskDefinition())
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    // when
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "A2")
        .migrate();

    // then — the migration is accepted
    Assertions.assertThat(
            RecordingExporter.processInstanceMigrationRecords(
                    ProcessInstanceMigrationIntent.MIGRATED)
                .withRecordKey(processInstanceKey)
                .getFirst())
        .describedAs(
            "migrating an element without an agent instance onto an agent element is allowed")
        .hasIntent(ProcessInstanceMigrationIntent.MIGRATED);
  }
}

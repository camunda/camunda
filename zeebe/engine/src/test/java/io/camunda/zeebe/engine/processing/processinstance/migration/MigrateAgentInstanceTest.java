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
import static org.assertj.core.api.AssertionsForClassTypes.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
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
                    .serviceTask("A", t -> t.zeebeJobType(AGENT_JOB_TYPE))
                    .userTask("B")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .versionTag("v2")
                    .startEvent()
                    .serviceTask("A2", t -> t.zeebeJobType(AGENT_JOB_TYPE))
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
        .hasVersionTag("v2")
        .describedAs("elementId is remapped despite \"A\" having no active element instance")
        .hasElementId("A2");
  }

  @Test
  public void shouldMigrateMultipleAgentInstancesOfServiceTasksInSameProcessInstance() {
    // given — two service tasks in parallel, each with its own agent instance, both still active
    // (neither job completes) at migration time; "A" is remapped to "A2" while "B" keeps its id
    // unchanged across the migration, requiring an explicit self-mapping since it stays active
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";
    final String otherJobType = "other-agent-job";

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .parallelGateway("fork")
                    .serviceTask("A", t -> t.zeebeJobType(AGENT_JOB_TYPE))
                    .parallelGateway("join")
                    .endEvent()
                    .moveToNode("fork")
                    .serviceTask("B", t -> t.zeebeJobType(otherJobType))
                    .connectTo("join")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .parallelGateway("fork2")
                    .serviceTask("A2", t -> t.zeebeJobType(AGENT_JOB_TYPE))
                    .parallelGateway("join2")
                    .endEvent()
                    .moveToNode("fork2")
                    .serviceTask("B", t -> t.zeebeJobType(otherJobType))
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
    final long firstAgentInstanceKey =
        engine
            .agentInstances()
            .withElementInstanceKey(firstTaskInstance.getKey())
            .create()
            .getKey();
    final long secondAgentInstanceKey =
        engine
            .agentInstances()
            .withElementInstanceKey(secondTaskInstance.getKey())
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
    assertThat(
            RecordingExporter.agentInstanceRecords(AgentInstanceIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(2))
        .extracting(Record::getValue)
        .extracting(
            AgentInstanceRecordValue::getAgentInstanceKey,
            AgentInstanceRecordValue::getProcessDefinitionKey,
            AgentInstanceRecordValue::getElementId)
        .describedAs(
            "Both agent instances move to the target process definition; \"A\"'s agent instance "
                + "is remapped to \"A2\" while \"B\"'s keeps its unchanged id")
        .containsExactlyInAnyOrder(
            tuple(firstAgentInstanceKey, targetProcessDefinitionKey, "A2"),
            tuple(secondAgentInstanceKey, targetProcessDefinitionKey, "B"));
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
}

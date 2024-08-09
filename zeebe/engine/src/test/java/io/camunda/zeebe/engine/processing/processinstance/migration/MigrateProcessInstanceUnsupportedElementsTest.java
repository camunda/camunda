/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance.migration;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class MigrateProcessInstanceUnsupportedElementsTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String SOURCE_PROCESS = "process_source";
  private static final String TARGET_PROCESS = "process_target";

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldRejectMigrationForActiveTimerIntermediateCatchEvent() {
    // given
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(SOURCE_PROCESS)
                    .startEvent()
                    .intermediateCatchEvent("timer1", c -> c.timerWithDuration("PT10S"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(TARGET_PROCESS)
                    .startEvent()
                    .intermediateCatchEvent("timer2", c -> c.timerWithDuration("PT10M"))
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey = extractTargetProcessDefinitionKey(deployment);

    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(SOURCE_PROCESS).create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("timer1")
        .await();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .migration()
            .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
            .addMappingInstruction("timer1", "timer2")
            .expectRejection()
            .migrate();

    // then
    assertThat(rejection).hasRejectionType(RejectionType.INVALID_STATE);
    Assertions.assertThat(rejection.getRejectionReason())
        .contains(
            String.format(
                """
      Expected to migrate process instance '%s' \
      but active element with id '%s' is intermediate catch event of type '%s'. \
      Migrating active elements with intermediate catch event of these types is not possible yet.""",
                processInstanceKey, "timer1", "TIMER"));
  }

  @Test
  public void shouldRejectMigrationForActiveReceiveTask() {
    // given
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(SOURCE_PROCESS)
                    .startEvent()
                    .receiveTask(
                        "A",
                        e -> e.message(m -> m.name("msg").zeebeCorrelationKeyExpression("key")))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(TARGET_PROCESS)
                    .startEvent()
                    .receiveTask(
                        "A",
                        e -> e.message(m -> m.name("msg").zeebeCorrelationKeyExpression("key")))
                    .userTask("B")
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey = extractTargetProcessDefinitionKey(deployment);

    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(SOURCE_PROCESS)
            .withVariable("key", helper.getCorrelationValue())
            .create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .migration()
            .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
            .addMappingInstruction("A", "A")
            .expectRejection()
            .migrate();

    // then
    assertThat(rejection).hasRejectionType(RejectionType.INVALID_STATE);
    Assertions.assertThat(rejection.getRejectionReason())
        .contains(
            String.format(
                """
                Expected to migrate process instance '%s' but active element with id '%s' \
                has an unsupported type. The migration of a %s is not supported""",
                processInstanceKey, "A", "RECEIVE_TASK"));
  }

  @Test
  public void shouldRejectMigrationForActiveExclusiveGateway() {
    // given
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(SOURCE_PROCESS)
                    .startEvent()
                    .exclusiveGateway("A")
                    .conditionExpression("missing_function_causing_incident()")
                    .endEvent()
                    .moveToLastExclusiveGateway()
                    .defaultFlow()
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(TARGET_PROCESS)
                    .startEvent()
                    .exclusiveGateway("A")
                    .conditionExpression("missing_function_causing_incident()")
                    .endEvent()
                    .moveToLastExclusiveGateway()
                    .defaultFlow()
                    .userTask("B")
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey = extractTargetProcessDefinitionKey(deployment);

    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(SOURCE_PROCESS).create();

    RecordingExporter.incidentRecords(IncidentIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .migration()
            .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
            .addMappingInstruction("A", "A")
            .expectRejection()
            .migrate();

    // then
    assertThat(rejection).hasRejectionType(RejectionType.INVALID_STATE);
    Assertions.assertThat(rejection.getRejectionReason())
        .contains(
            String.format(
                """
                Expected to migrate process instance '%s' but active element with id '%s' \
                has an unsupported type. The migration of a %s is not supported""",
                processInstanceKey, "A", "EXCLUSIVE_GATEWAY"));
  }

  @Test
  public void shouldRejectMigrationForActiveInclusiveGateway() {
    // given
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(SOURCE_PROCESS)
                    .startEvent()
                    .inclusiveGateway("A")
                    .conditionExpression("missing_function_causing_incident()")
                    .endEvent()
                    .moveToLastInclusiveGateway()
                    .defaultFlow()
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(TARGET_PROCESS)
                    .startEvent()
                    .inclusiveGateway("A")
                    .conditionExpression("missing_function_causing_incident()")
                    .endEvent()
                    .moveToLastInclusiveGateway()
                    .defaultFlow()
                    .userTask("B")
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey = extractTargetProcessDefinitionKey(deployment);

    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(SOURCE_PROCESS).create();

    RecordingExporter.incidentRecords(IncidentIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .migration()
            .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
            .addMappingInstruction("A", "A")
            .expectRejection()
            .migrate();

    // then
    assertThat(rejection).hasRejectionType(RejectionType.INVALID_STATE);
    Assertions.assertThat(rejection.getRejectionReason())
        .contains(
            String.format(
                """
                Expected to migrate process instance '%s' but active element with id '%s' \
                has an unsupported type. The migration of a %s is not supported""",
                processInstanceKey, "A", "INCLUSIVE_GATEWAY"));
  }

  @Test
  public void shouldRejectMigrationForActiveEventBasedGateway() {
    // given
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(SOURCE_PROCESS)
                    .startEvent()
                    .eventBasedGateway("A")
                    .intermediateCatchEvent(
                        "MSG_1",
                        e -> e.message(m -> m.name("msg_1").zeebeCorrelationKeyExpression("key")))
                    .endEvent()
                    .moveToLastGateway()
                    .intermediateCatchEvent(
                        "MSG_2",
                        e -> e.message(m -> m.name("msg_2").zeebeCorrelationKeyExpression("key")))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(TARGET_PROCESS)
                    .startEvent()
                    .eventBasedGateway("A")
                    .intermediateCatchEvent(
                        "MSG_1",
                        e -> e.message(m -> m.name("msg_1").zeebeCorrelationKeyExpression("key")))
                    .endEvent()
                    .moveToLastGateway()
                    .intermediateCatchEvent(
                        "MSG_2",
                        e -> e.message(m -> m.name("msg_2").zeebeCorrelationKeyExpression("key")))
                    .endEvent()
                    .moveToLastGateway()
                    .intermediateCatchEvent(
                        "MSG_3",
                        e -> e.message(m -> m.name("msg_3").zeebeCorrelationKeyExpression("key")))
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey = extractTargetProcessDefinitionKey(deployment);

    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(SOURCE_PROCESS)
            .withVariable("key", helper.getCorrelationValue())
            .create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .migration()
            .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
            .addMappingInstruction("A", "A")
            .expectRejection()
            .migrate();

    // then
    assertThat(rejection).hasRejectionType(RejectionType.INVALID_STATE);
    Assertions.assertThat(rejection.getRejectionReason())
        .contains(
            String.format(
                """
                Expected to migrate process instance '%s' but active element with id '%s' \
                has an unsupported type. The migration of a %s is not supported""",
                processInstanceKey, "A", "EVENT_BASED_GATEWAY"));
  }

  @Test
  public void shouldRejectMigrationForActiveEventBasedSubProcess() {
    // given
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(SOURCE_PROCESS)
                    .eventSubProcess(
                        "SUB",
                        s ->
                            s.startEvent()
                                .message(m -> m.name("msg").zeebeCorrelationKeyExpression("key"))
                                .userTask("A")
                                .endEvent())
                    .startEvent()
                    .userTask("B")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(TARGET_PROCESS)
                    .eventSubProcess(
                        "SUB",
                        s ->
                            s.startEvent()
                                .message(m -> m.name("msg").zeebeCorrelationKeyExpression("key"))
                                .userTask("A")
                                .endEvent())
                    .startEvent()
                    .userTask("B")
                    .userTask("C")
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey = extractTargetProcessDefinitionKey(deployment);

    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(SOURCE_PROCESS)
            .withVariable("key", helper.getCorrelationValue())
            .create();

    ENGINE.message().withName("msg").withCorrelationKey(helper.getCorrelationValue()).publish();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .migration()
            .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
            .addMappingInstruction("A", "A")
            .expectRejection()
            .migrate();

    // then
    assertThat(rejection).hasRejectionType(RejectionType.INVALID_STATE);
    Assertions.assertThat(rejection.getRejectionReason())
        .contains(
            """
                Expected to migrate process instance but process instance has an event subprocess. \
                Process instances with event subprocesses cannot be migrated yet.""");
  }

  @Test
  public void shouldRejectMigrationForActiveMultiInstance() {
    // given
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(SOURCE_PROCESS)
                    .startEvent()
                    .userTask(
                        "A",
                        u ->
                            u.multiInstance()
                                .zeebeInputCollectionExpression("[1,2]")
                                .zeebeInputElement("input"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(TARGET_PROCESS)
                    .startEvent()
                    .userTask(
                        "A",
                        u ->
                            u.multiInstance()
                                .zeebeInputCollectionExpression("[1,2]")
                                .zeebeInputElement("input"))
                    .userTask("B")
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey = extractTargetProcessDefinitionKey(deployment);

    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(SOURCE_PROCESS).create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .migration()
            .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
            .addMappingInstruction("A", "A")
            .expectRejection()
            .migrate();

    // then
    assertThat(rejection).hasRejectionType(RejectionType.INVALID_STATE);
    Assertions.assertThat(rejection.getRejectionReason())
        .contains(
            String.format(
                """
                Expected to migrate process instance '%s' but active element with id '%s' \
                has an unsupported type. The migration of a %s is not supported""",
                processInstanceKey, "A", "MULTI_INSTANCE_BODY"));
  }

  @Test
  public void shouldRejectMigrationForActiveBusinessRuleTask() {
    // given
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(SOURCE_PROCESS)
                    .startEvent()
                    .businessRuleTask(
                        "A", b -> b.zeebeCalledDecisionId("decision").zeebeResultVariable("result"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(TARGET_PROCESS)
                    .startEvent()
                    .businessRuleTask(
                        "A", b -> b.zeebeCalledDecisionId("decision").zeebeResultVariable("result"))
                    .userTask("B")
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey = extractTargetProcessDefinitionKey(deployment);

    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(SOURCE_PROCESS).create();

    RecordingExporter.incidentRecords(IncidentIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .migration()
            .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
            .addMappingInstruction("A", "A")
            .expectRejection()
            .migrate();

    // then
    assertThat(rejection).hasRejectionType(RejectionType.INVALID_STATE);
    Assertions.assertThat(rejection.getRejectionReason())
        .contains(
            String.format(
                """
                Expected to migrate process instance '%s' but active element with id '%s' \
                has an unsupported type. The migration of a %s is not supported""",
                processInstanceKey, "A", "BUSINESS_RULE_TASK"));
  }

  private static long extractTargetProcessDefinitionKey(
      final Record<DeploymentRecordValue> deployment) {
    return deployment.getValue().getProcessesMetadata().stream()
        .filter(p -> p.getBpmnProcessId().equals(TARGET_PROCESS))
        .findAny()
        .orElseThrow()
        .getProcessDefinitionKey();
  }
}

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

  private static long extractTargetProcessDefinitionKey(
      final Record<DeploymentRecordValue> deployment) {
    return deployment.getValue().getProcessesMetadata().stream()
        .filter(p -> p.getBpmnProcessId().equals(TARGET_PROCESS))
        .findAny()
        .orElseThrow()
        .getProcessDefinitionKey();
  }
}

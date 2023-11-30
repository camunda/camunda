/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class MigrateProcessInstanceRejectionTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldRejectCommandWhenProcessInstanceIsUnknown() {
    // given
    final long unknownKey = 12345L;

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(unknownKey)
        .migration()
        .withTargetProcessDefinitionKey(1L)
        .expectRejection()
        .migrate();

    // then
    final var rejectionRecord =
        RecordingExporter.processInstanceMigrationRecords().onlyCommandRejections().getFirst();

    assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceMigrationIntent.MIGRATE)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            String.format(
                "Expected to migrate process instance but no process instance found with key '%d'",
                unknownKey))
        .hasKey(unknownKey);
  }

  @Test
  public void shouldRejectCommandWhenTargetProcessDefinitionIsUnknown() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("task"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();

    final long unknownKey = 12345L;

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(unknownKey)
        .expectRejection()
        .migrate();

    // then
    final var rejectionRecord =
        RecordingExporter.processInstanceMigrationRecords().onlyCommandRejections().getFirst();

    assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceMigrationIntent.MIGRATE)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            String.format(
                "Expected to migrate process instance to process definition but no process definition found with key '%d'",
                unknownKey))
        .hasKey(processInstanceKey);
  }

  @Test
  public void shouldRejectCommandWhenActiveElementIsNotMapped() {
    // given
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess("process")
                    .startEvent()
                    .serviceTask("A", t -> t.zeebeJobType("task"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess("process2")
                    .startEvent()
                    .serviceTask("A", t -> t.zeebeJobType("task"))
                    .userTask("B")
                    .endEvent()
                    .done())
            .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();

    final long targetProcessDefinitionKey =
        extractTargetProcessDefinitionKey(deployment, "process2");

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .expectRejection()
        .migrate();

    // then
    final var rejectionRecord =
        RecordingExporter.processInstanceMigrationRecords().onlyCommandRejections().getFirst();

    assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceMigrationIntent.MIGRATE)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            String.format(
                """
                Expected to migrate process instance '%d' \
                but no mapping instruction defined for active element with id 'A'. \
                Elements cannot be migrated without a mapping.""",
                processInstanceKey))
        .hasKey(processInstanceKey);
  }

  private static long extractTargetProcessDefinitionKey(
      final Record<DeploymentRecordValue> deployment, final String bpmnProcessId) {
    return deployment.getValue().getProcessesMetadata().stream()
        .filter(p -> p.getBpmnProcessId().equals(bpmnProcessId))
        .findAny()
        .orElseThrow()
        .getProcessDefinitionKey();
  }
}

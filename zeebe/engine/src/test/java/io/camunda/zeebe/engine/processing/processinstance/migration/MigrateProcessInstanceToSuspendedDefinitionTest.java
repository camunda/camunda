/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance.migration;

import static io.camunda.zeebe.protocol.record.RecordAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verifies that a process instance can't be migrated onto a suspended process definition version,
 * while migrating away from a suspended version stays allowed.
 */
public class MigrateProcessInstanceToSuspendedDefinitionTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldRejectMigrationToSuspendedTargetProcessDefinition() {
    // given
    final var source = deploy(helper.getBpmnProcessId() + "-source", "A");
    final var target = deploy(helper.getBpmnProcessId() + "-target", "B");
    final long processInstanceKey = createInstance(source);
    suspend(target);

    // when
    final var rejection =
        engine
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .migration()
            .withTargetProcessDefinitionKey(target.getProcessDefinitionKey())
            .addMappingInstruction("A", "B")
            .expectRejection()
            .migrate();

    // then
    assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            "Expected to migrate process instance to process definition with ID '%s' and version %d (key %d), but it is suspended"
                .formatted(
                    target.getBpmnProcessId(),
                    target.getVersion(),
                    target.getProcessDefinitionKey()));
    // the rejected command leaves the instance untouched: the first MIGRATED record is written by
    // the later successful migration, after the rejection
    engine
        .processDefinitionSuspension()
        .withProcessDefinitionKey(target.getProcessDefinitionKey())
        .resume();
    migrate(processInstanceKey, target);
    assertThat(
            RecordingExporter.processInstanceMigrationRecords(
                    ProcessInstanceMigrationIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(1)
                .map(Record::getPosition))
        .hasSize(1)
        .allMatch(position -> position > rejection.getPosition());
  }

  @Test
  public void shouldMigrateFromSuspendedSourceToActiveTarget() {
    // given
    final var source = deploy(helper.getBpmnProcessId() + "-source", "A");
    final var target = deploy(helper.getBpmnProcessId() + "-target", "B");
    final long processInstanceKey = createInstance(source);
    suspend(source);

    // when
    migrate(processInstanceKey, target);

    // then
    assertMigrated(processInstanceKey);
  }

  @Test
  public void shouldMigrateWithinSameSuspendedVersion() {
    // given
    final var process = deploy(helper.getBpmnProcessId(), "A");
    final long processInstanceKey = createInstance(process);
    suspend(process);

    // when
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(process.getProcessDefinitionKey())
        .addMappingInstruction("A", "A")
        .migrate();

    // then
    assertMigrated(processInstanceKey);
  }

  @Test
  public void shouldMigrateToTargetAfterResume() {
    // given
    final var source = deploy(helper.getBpmnProcessId() + "-source", "A");
    final var target = deploy(helper.getBpmnProcessId() + "-target", "B");
    final long processInstanceKey = createInstance(source);
    suspend(target);
    engine
        .processDefinitionSuspension()
        .withProcessDefinitionKey(target.getProcessDefinitionKey())
        .resume();

    // when
    migrate(processInstanceKey, target);

    // then
    assertMigrated(processInstanceKey);
  }

  private ProcessMetadataValue deploy(final String processId, final String taskId) {
    return engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId).startEvent().userTask(taskId).endEvent().done())
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .getFirst();
  }

  private long createInstance(final ProcessMetadataValue process) {
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId(process.getBpmnProcessId()).create();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.USER_TASK)
        .await();
    return processInstanceKey;
  }

  private void suspend(final ProcessMetadataValue process) {
    engine
        .processDefinitionSuspension()
        .withProcessDefinitionKey(process.getProcessDefinitionKey())
        .suspend();
  }

  private void migrate(final long processInstanceKey, final ProcessMetadataValue target) {
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(target.getProcessDefinitionKey())
        .addMappingInstruction("A", "B")
        .migrate();
  }

  private void assertMigrated(final long processInstanceKey) {
    assertThat(
            RecordingExporter.processInstanceMigrationRecords(
                    ProcessInstanceMigrationIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }
}

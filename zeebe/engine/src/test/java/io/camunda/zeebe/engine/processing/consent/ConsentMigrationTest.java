/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.consent;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * A job keeps the consent declaration it was created with. A migration that adds, removes or
 * changes the declaration of its task would hand the job out under rules the task no longer
 * declares, so it is rejected.
 */
public final class ConsentMigrationTest {

  private static final String CONNECTION = "hibob";
  private static final String OPERATION = "region.update";
  private static final String JOB_TYPE = "hibob-region-update";

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Before
  public void setUp() {
    engine
        .clusterVariables()
        .withName(ConsentConnections.VARIABLE_PREFIX + CONNECTION)
        .setGlobalScope()
        .withValue(
            Map.of(
                "id",
                CONNECTION,
                "operations",
                List.of(Map.of("id", OPERATION, "consent", "required", "scope", "either"))))
        .create();
  }

  @Test
  public void shouldRejectMigratingAnUngatedJobToATaskThatDeclaresConsent() {
    // given a job created without the gate
    deploy("ungated", false);
    final long gated = deploy("gated-target", true);
    final long processInstanceKey = start("ungated");
    awaitJob(processInstanceKey);

    // when it is migrated to a task that declares consent
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(gated)
        .addMappingInstruction("task", "task")
        .expectRejection()
        .migrate();

    // then the migration is rejected, and the job would never sit unasked behind the gate
    final var rejection =
        RecordingExporter.processInstanceMigrationRecords().onlyCommandRejections().getFirst();
    Assertions.assertThat(rejection)
        .hasIntent(ProcessInstanceMigrationIntent.MIGRATE)
        .hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .contains("declares consent as 'none'")
        .contains("declares it as 'CONSENT hibob/region.update");
  }

  @Test
  public void shouldRejectMigratingAJobWaitingForConsentToATaskWithoutIt() {
    // given a job that waits for consent
    deploy("gated", true);
    final long processInstanceKey = start("gated");
    awaitJob(processInstanceKey);
    final long ungated = deploy("ungated-target", false);

    // when it is migrated to a task without the gate
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(ungated)
        .addMappingInstruction("task", "task")
        .expectRejection()
        .migrate();

    // then the migration is rejected
    assertThat(
            RecordingExporter.processInstanceMigrationRecords()
                .onlyCommandRejections()
                .getFirst()
                .getRejectionReason())
        .contains("declares consent as 'CONSENT hibob/region.update")
        .contains("declares it as 'none'");
  }

  @Test
  public void shouldMigrateAJobWhoseTaskDeclaresTheSameConsent() {
    // given a job that waits for consent
    deploy("gated", true);
    final long processInstanceKey = start("gated");
    awaitJob(processInstanceKey);
    final long sameDeclaration = deploy("gated-again", true);

    // when it is migrated to a task with the same declaration
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(sameDeclaration)
        .addMappingInstruction("task", "task")
        .migrate();

    // then the job moves with it
    assertThat(
            RecordingExporter.jobRecords(JobIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  private long deploy(final String processId, final boolean gated) {
    return engine
        .deployment()
        .withXmlResource(model(processId, gated))
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .getFirst()
        .getProcessDefinitionKey();
  }

  private long start(final String processId) {
    return engine
        .processInstance()
        .ofBpmnProcessId(processId)
        .withVariables(Map.of("employee", "dana", "newRegion", "DACH"))
        .create();
  }

  private static void awaitJob(final long processInstanceKey) {
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();
  }

  private static BpmnModelInstance model(final String processId, final boolean gated) {
    return Bpmn.createExecutableProcess(processId)
        .startEvent()
        .serviceTask(
            "task",
            t -> {
              t.zeebeJobType(JOB_TYPE);
              if (gated) {
                t.zeebeConsent(CONNECTION, OPERATION)
                    .zeebeConsentSubjectExpression("employee")
                    .zeebeConsentChangeExpression("{region: newRegion}");
              }
            })
        .endEvent()
        .done();
  }
}

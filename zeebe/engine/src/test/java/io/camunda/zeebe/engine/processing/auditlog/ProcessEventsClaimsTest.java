/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.auditlog;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class ProcessEventsClaimsTest {

  private static final String PROCESS_ID = "testProcess";
  private static final String PROCESS_ID_V2 = "testProcessV2";

  private static final ConfiguredUser DEFAULT_USER =
      new ConfiguredUser(
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString());

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withIdentitySetup()
          .withSecurityConfig(
              cfg -> {
                cfg.getAuthorizations().setEnabled(true);
                cfg.getInitialization().setUsers(List.of(DEFAULT_USER));
                cfg.getInitialization()
                    .getDefaultRoles()
                    .put("admin", Map.of("users", List.of(DEFAULT_USER.getUsername())));
              });

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldIncludeClaimsInProcessInstanceCreationCreatedEvents() {
    // given
    deployProcess();

    // when
    final var processInstanceKey =
        engine.processInstance().ofBpmnProcessId(PROCESS_ID).create(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.processInstanceCreationRecords()
            .withIntent(ProcessInstanceCreationIntent.CREATED)
            .withInstanceKey(processInstanceKey)
            .findFirst();
    assertAuthorizationClaims(record);
    assertThat(record.get().getValue().getRootProcessInstanceKey()).isEqualTo(processInstanceKey);
  }

  @Test
  public void shouldKeepOnlyActorClaimsOnFollowUpRecords() {
    // given
    deployProcess();
    final var authInfo =
        new AuthInfo()
            .setClaims(
                Map.of(
                    Authorization.AUTHORIZED_USERNAME,
                    DEFAULT_USER.getUsername(),
                    Authorization.USER_GROUPS_CLAIMS,
                    List.of("group"),
                    Authorization.USER_TOKEN_CLAIMS,
                    Map.of("sub", DEFAULT_USER.getUsername())));

    // when
    final var processInstanceKey =
        engine.processInstance().ofBpmnProcessId(PROCESS_ID).create(authInfo);

    // then
    final var records =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .limit(
                r ->
                    r.getIntent() == ProcessInstanceIntent.ELEMENT_ACTIVATED
                        && r.getValue().getBpmnElementType() == BpmnElementType.SERVICE_TASK)
            .toList();
    assertThat(records)
        .extracting(Record::getRecordType)
        .contains(RecordType.COMMAND, RecordType.EVENT);
    assertThat(records)
        .allSatisfy(
            r ->
                assertThat(r.getAuthorizations())
                    .isEqualTo(
                        Map.of(Authorization.AUTHORIZED_USERNAME, DEFAULT_USER.getUsername())));
    assertThat(
            RecordingExporter.processInstanceCreationRecords()
                .withIntent(ProcessInstanceCreationIntent.CREATE)
                .onlyCommands()
                .getFirst()
                .getAuthorizations())
        .as("client command")
        .isEqualTo(authInfo.getClaims());
  }

  @Test
  public void shouldIncludeClaimsInProcessInstanceModificationModifiedEvents() {
    // given
    deployProcessWithServiceTask();
    final var processInstanceKey =
        engine.processInstance().ofBpmnProcessId(PROCESS_ID).create(DEFAULT_USER.getUsername());

    // when
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("taskB")
        .modify(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.processInstanceModificationRecords(
                ProcessInstanceModificationIntent.MODIFIED)
            .withProcessInstanceKey(processInstanceKey)
            .findFirst();
    assertAuthorizationClaims(record);
    assertThat(record.get().getValue().getRootProcessInstanceKey()).isEqualTo(processInstanceKey);
  }

  @Test
  public void shouldIncludeClaimsInProcessInstanceMigrationMigratedEvents() {
    // given
    deployProcess();
    final var targetProcessDefinitionKey = deployProcessV2();
    final var processInstanceKey =
        engine.processInstance().ofBpmnProcessId(PROCESS_ID).create(DEFAULT_USER.getUsername());

    // when
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("taskA", "taskA")
        .migrate(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.processInstanceMigrationRecords(ProcessInstanceMigrationIntent.MIGRATED)
            .withProcessInstanceKey(processInstanceKey)
            .findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInProcessInstanceCancelingEvents() {
    // given
    deployProcess();
    final var processInstanceKey =
        engine.processInstance().ofBpmnProcessId(PROCESS_ID).create(DEFAULT_USER.getUsername());

    // when
    engine.processInstance().withInstanceKey(processInstanceKey).cancel(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.CANCELING)
            .withProcessInstanceKey(processInstanceKey)
            .findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInIncidentResolvedEvents() {
    // given
    deployProcessWithIncident();
    final var processInstanceKey =
        engine.processInstance().ofBpmnProcessId(PROCESS_ID).create(DEFAULT_USER.getUsername());
    // create incident by failing the job
    final var jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();
    engine.job().withKey(jobKey).withRetries(-1).fail();
    final var incidentKey =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();
    // Fix the issue by setting the variable
    engine
        .variables()
        .ofScope(processInstanceKey)
        .withDocument(Map.of("x", 10))
        .update(DEFAULT_USER.getUsername());
    engine.job().withKey(jobKey).withRetries(1).updateRetries();

    // when
    engine
        .incident()
        .ofInstance(processInstanceKey)
        .withKey(incidentKey)
        .resolve(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.incidentRecords(IncidentIntent.RESOLVED)
            .withRecordKey(incidentKey)
            .findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInDecisionEvaluationEvaluatedEvents() {
    // given
    deployDecision("/dmn/decision-table.dmn");

    // when
    engine
        .decision()
        .ofDecisionId("jedi_or_sith")
        .withVariable("lightsaberColor", "blue")
        .evaluate(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.decisionEvaluationRecords(DecisionEvaluationIntent.EVALUATED).findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInDecisionEvaluationFailedEvents() {
    // given
    deployDecision("/dmn/drg-force-user-with-assertions.dmn");

    // when - evaluate without required variable
    engine
        .decision()
        .ofDecisionId("jedi_or_sith")
        .expectFailure()
        .evaluate(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.decisionEvaluationRecords(DecisionEvaluationIntent.FAILED).findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInVariableCreatedEvents() {
    // given
    deployProcess();
    final var processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("testVar", "initialValue")
            .create(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("testVar")
            .findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInVariableUpdatedEvents() {
    // given
    deployProcess();
    final var processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("testVar", "initialValue")
            .create(DEFAULT_USER.getUsername());

    // when - update the variable
    engine
        .variables()
        .ofScope(processInstanceKey)
        .withDocument(Map.of("testVar", "updatedValue"))
        .update(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.variableRecords(VariableIntent.UPDATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("testVar")
            .findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInProcessInstanceMigrationRejectionEvents() {
    // given
    final var deployment =
        engine
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
                    .userTask("A")
                    .endEvent()
                    .done())
            .deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId("process").create();

    final long targetProcessDefinitionKey =
        extractTargetProcessDefinitionKey(deployment, "process2");

    // when
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "A")
        .expectRejection()
        .migrate(DEFAULT_USER.getUsername());

    // then - verify the rejection record contains authorization claims
    final var rejectionRecord =
        RecordingExporter.processInstanceMigrationRecords().onlyCommandRejections().getFirst();
    assertThat(rejectionRecord.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejectionRecord.getAuthorizations())
        .containsEntry(Authorization.AUTHORIZED_USERNAME, DEFAULT_USER.getUsername());
  }

  @Test
  public void shouldIncludeClaimsInProcessInstanceCancelRejectionEvents() {
    // given - deploy a process with a call activity that creates a child process instance
    deployProcessWithCallActivity();
    deployChildProcess();

    final var processInstanceKey =
        engine.processInstance().ofBpmnProcessId(PROCESS_ID).create(DEFAULT_USER.getUsername());

    // Get the child process instance key from the call activity's activated record
    final var childProcessInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(processInstanceKey)
            .withBpmnProcessId("childProcess")
            .getFirst()
            .getValue()
            .getProcessInstanceKey();

    // when - attempt to cancel a child process instance directly (should be rejected)
    // Child process instances can only be canceled through their parent
    final var rejectionRecord =
        engine
            .processInstance()
            .withInstanceKey(childProcessInstanceKey)
            .expectRejection()
            .cancel(DEFAULT_USER.getUsername());

    // then - verify the rejection record contains authorization claims
    assertThat(rejectionRecord.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejectionRecord.getAuthorizations())
        .containsEntry(Authorization.AUTHORIZED_USERNAME, DEFAULT_USER.getUsername());
  }

  @Test
  public void shouldIncludeClaimsInIncidentResolveRejectionEvents() {
    // given
    deployProcessWithIncident();
    final var processInstanceKey =
        engine.processInstance().ofBpmnProcessId(PROCESS_ID).create(DEFAULT_USER.getUsername());
    // create incident by failing the job
    final var jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();
    engine.job().withKey(jobKey).withRetries(0).fail();
    final var incidentKey =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();

    // when - attempt to resolve the incident (should be rejected with INVALID_STATE)
    final var rejectionRecord =
        engine
            .incident()
            .ofInstance(processInstanceKey)
            .withKey(incidentKey)
            .expectRejection()
            .resolve(DEFAULT_USER.getUsername());

    // then - verify the rejection record contains authorization claims
    assertThat(rejectionRecord.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejectionRecord.getAuthorizations())
        .containsEntry(Authorization.AUTHORIZED_USERNAME, DEFAULT_USER.getUsername());
  }

  private long deployProcess() {
    return engine
        .deployment()
        .withXmlResource(
            "process.bpmn",
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("taskA", t -> t.zeebeJobType("taskA"))
                .endEvent()
                .done())
        .deploy(DEFAULT_USER.getUsername())
        .getValue()
        .getProcessesMetadata()
        .getFirst()
        .getProcessDefinitionKey();
  }

  private long deployProcessV2() {
    return engine
        .deployment()
        .withXmlResource(
            "process_v2.bpmn",
            Bpmn.createExecutableProcess(PROCESS_ID_V2)
                .startEvent()
                .serviceTask("taskA", t -> t.zeebeJobType("taskA"))
                .endEvent()
                .done())
        .deploy(DEFAULT_USER.getUsername())
        .getValue()
        .getProcessesMetadata()
        .getFirst()
        .getProcessDefinitionKey();
  }

  private void deployProcessWithServiceTask() {
    engine
        .deployment()
        .withXmlResource(
            "process.bpmn",
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("taskA", t -> t.zeebeJobType("taskA"))
                .serviceTask("taskB", t -> t.zeebeJobType("taskB"))
                .endEvent()
                .done())
        .deploy(DEFAULT_USER.getUsername());
  }

  private void deployProcessWithIncident() {
    engine
        .deployment()
        .withXmlResource(
            "process.bpmn",
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("task").zeebeInputExpression("x", "y"))
                .endEvent()
                .done())
        .deploy(DEFAULT_USER.getUsername());
  }

  private void deployDecision(final String decisionResource) {
    engine
        .deployment()
        .withXmlClasspathResource(decisionResource)
        .deploy(DEFAULT_USER.getUsername());
  }

  private void deployProcessWithCallActivity() {
    engine
        .deployment()
        .withXmlResource(
            "process.bpmn",
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .callActivity("callActivity", c -> c.zeebeProcessId("childProcess"))
                .endEvent()
                .done())
        .deploy(DEFAULT_USER.getUsername());
  }

  private void deployChildProcess() {
    engine
        .deployment()
        .withXmlResource(
            "childProcess.bpmn",
            Bpmn.createExecutableProcess("childProcess")
                .startEvent()
                .serviceTask("childTask", t -> t.zeebeJobType("childTask"))
                .endEvent()
                .done())
        .deploy(DEFAULT_USER.getUsername());
  }

  private void assertAuthorizationClaims(final java.util.Optional<?> record) {
    assertThat(record).isPresent();
    assertThat(((io.camunda.zeebe.protocol.record.Record<?>) record.get()).getAuthorizations())
        .containsEntry(Authorization.AUTHORIZED_USERNAME, DEFAULT_USER.getUsername());
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

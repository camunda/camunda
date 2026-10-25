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
import io.camunda.zeebe.engine.util.SecretStoreRegistries;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ConsentIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ConsentDecision;
import io.camunda.zeebe.protocol.record.value.ConsentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * The scenarios of the consent pattern built in BPMN, run against the consent gate in the engine.
 * The numbers match {@code consent-pattern/scenarios/run.py} in camunda/on-behallf-of.
 */
public final class ConsentGateTest {

  private static final String CONNECTION = "hibob";
  private static final String OPERATION = "region.update";
  private static final String NEW_HIRE_OPERATION = "region.update.newhire";
  private static final String JOB_TYPE = "hibob-region-update";
  private static final String PROCESS_ID = "employee-region-change";
  private static final String CREDENTIAL = "HIBOB_SERVICE_USER";

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withSecretStoreRegistry(SecretStoreRegistries.resolveAll("service-user-token"));

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
                "name",
                "HiBob",
                "credential",
                "camunda.secrets." + CREDENTIAL,
                "operations",
                List.of(
                    Map.of(
                        "id",
                        OPERATION,
                        "label",
                        "change your Region",
                        "consent",
                        "required",
                        "scope",
                        "either"),
                    Map.of(
                        "id",
                        NEW_HIRE_OPERATION,
                        "label",
                        "change the Region of a new hire",
                        "relationship",
                        List.of("subject.work.reportsTo.id"),
                        "preconditions",
                        List.of("subject.internal.lifecycleStatus == \"hired\"")))))
        .create();
    engine.deployment().withXmlResource(regionChange()).deploy();
  }

  @Test
  public void shouldStampTheRequesterOnTheProcessInstance() {
    // when
    final long processInstanceKey = startChange("alice", "DACH", "dana");

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .getFirst()
                .getValue()
                .getRequester())
        .isEqualTo("dana");
  }

  @Test
  public void shouldParkTheJobUntilTheSubjectConsents() {
    // when
    final long processInstanceKey = startChange("alice", "DACH", "alice");

    // then
    final var request = requestOf(processInstanceKey);
    assertThat(request.getSubject()).isEqualTo("alice");
    assertThat(request.getRequester()).isEqualTo("alice");
    assertThat(request.getChange()).containsEntry("region", "DACH");
    assertThat(activate()).isEmpty();
  }

  @Test
  public void shouldReleaseEveryParkedJobOfTheSubjectWithOneGrant() {
    // given scenario 1: three requests for Alice at once
    final var first = startChange("alice", "DACH", "alice");
    startChange("alice", "DACH", "alice");
    startChange("alice", "DACH", "alice");
    final long jobKey = requestOf(first).getJobKey();

    // when
    final var answer = engine.consent().resolve(jobKey, ConsentDecision.GRANT, "alice");

    // then
    assertThat(answer.getIntent()).isEqualTo(ConsentIntent.APPROVED);
    assertThat(
            RecordingExporter.records()
                .withValueType(ValueType.CONSENT)
                .withIntent(ConsentIntent.GRANTED)
                .exists())
        .isTrue();
    assertThat(activate()).hasSize(3);
  }

  @Test
  public void shouldHandOutTheNextJobWithoutAskingUnderAGrant() {
    // given scenario 2: Alice said yes, always
    engine.consent().grant(CONNECTION, OPERATION, "alice");

    // when
    startChange("alice", "Africa", "alice");

    // then
    final var jobs = activate();
    assertThat(jobs).hasSize(1);
    assertThat(proofOf(jobs.getFirst())).containsEntry("basis", "GRANT");
  }

  @Test
  public void shouldParkAgainAfterARevocationAndBindAnApprovalToItsInstance() {
    // given scenario 3: Alice revokes
    final var grant = engine.consent().grant(CONNECTION, OPERATION, "alice");
    engine.consent().revoke(grant.getKey(), "", "alice");

    // when the next change asks, and she approves it once
    final var first = startChange("alice", "Benelux & France", "alice");
    engine.consent().resolve(requestOf(first).getJobKey(), ConsentDecision.APPROVE_ONCE, "alice");

    // then the approval covers its own instance only
    final var jobs = activate();
    assertThat(jobs).hasSize(1);
    assertThat(proofOf(jobs.getFirst())).containsEntry("basis", "APPROVAL");
    final var second = startChange("alice", "Benelux & France", "alice");
    assertThat(requestOf(second).getJobKey()).isPositive();
    assertThat(activate()).isEmpty();
  }

  @Test
  public void shouldThrowConsentDeclinedToTheBoundaryEventWithoutAWorker() {
    // given scenario 4
    final var processInstanceKey = startChange("dana", "LATAM", "dana");

    // when
    final var answer =
        engine
            .consent()
            .resolve(requestOf(processInstanceKey).getJobKey(), ConsentDecision.DECLINE, "dana");

    // then
    assertThat(answer.getIntent()).isEqualTo(ConsentIntent.DECLINED);
    assertThat(endEventOf(processInstanceKey)).isEqualTo("refused");
  }

  @Test
  public void shouldFireTheBoundaryTimerWhileTheJobIsParked() {
    // given scenario 5: nobody answers
    final var processInstanceKey = startChange("dana", "APAC", "dana");
    final long jobKey = requestOf(processInstanceKey).getJobKey();

    // when
    engine.increaseTime(Duration.ofDays(15));

    // then
    assertThat(endEventOf(processInstanceKey)).isEqualTo("no-answer");
    assertThat(RecordingExporter.jobRecords(JobIntent.CANCELED).withRecordKey(jobKey).exists())
        .isTrue();
    final var late = engine.consent().resolve(jobKey, ConsentDecision.APPROVE_ONCE, "dana");
    assertThat(late.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(late.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRefuseAModelThatReferencesTheConnectionCredentialWithoutTheGate() {
    // given scenario 6: a model that skips the gate and reads the credential itself
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("consent-bypass")
                .startEvent()
                .serviceTask(
                    "task",
                    t ->
                        t.zeebeJobType(JOB_TYPE)
                            .zeebeInputExpression("camunda.secrets." + CREDENTIAL, "token"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("consent-bypass").create("dana");

    // then
    final var incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(incident.getValue().getErrorMessage())
        .contains("zeebe:consent", "connection 'hibob'");
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getIntent() == IncidentIntent.CREATED)
                .jobRecords()
                .withIntent(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isFalse();
  }

  @Test
  public void shouldHandOutNoCredentialToAJobThatSkipsTheGate() {
    // given scenario 6: the same job type, without the gate and without the secret
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("consent-bypass-quiet")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
                .endEvent()
                .done())
        .deploy();

    // when
    engine.processInstance().ofBpmnProcessId("consent-bypass-quiet").create("dana");

    // then the worker receives no proof and no credential, so it has nothing to write with
    final var jobs = activate();
    assertThat(jobs).hasSize(1);
    assertThat(jobs.getFirst().getVariables()).doesNotContainKey(ConsentGate.CONSENT_VARIABLE);
    assertThat(jobs.getFirst().getSecretReferences()).isEmpty();
  }

  @Test
  public void shouldLetOnlyTheSubjectAnswerTheRequest() {
    // given scenarios 7, 10 and 11: Dana asks; Alice and Sam try to answer for her
    final long jobKey = requestOf(startChange("dana", "NA", "dana")).getJobKey();

    // when
    final var byAlice = engine.consent().resolve(jobKey, ConsentDecision.APPROVE_ONCE, "alice");
    final var bySam = engine.consent().resolve(jobKey, ConsentDecision.GRANT, "sam");

    // then
    assertThat(byAlice.getRejectionType()).isEqualTo(RejectionType.FORBIDDEN);
    assertThat(byAlice.getRejectionReason()).contains("Only 'dana' can answer");
    assertThat(bySam.getRejectionType()).isEqualTo(RejectionType.FORBIDDEN);
    assertThat(activate()).isEmpty();
  }

  @Test
  public void shouldAskTheSubjectWhenSomeoneElseStartsTheChange() {
    // given scenario 13: Dana starts a change of Alice's Region
    final long processInstanceKey = startChange("alice", "Middle East", "dana");

    // when Dana tries to approve it herself
    final var request = requestOf(processInstanceKey);
    final var byDana =
        engine.consent().resolve(request.getJobKey(), ConsentDecision.APPROVE_ONCE, "dana");

    // then the request is Alice's, it names Dana as the requester, and Dana cannot answer it
    assertThat(request.getSubject()).isEqualTo("alice");
    assertThat(request.getRequester()).isEqualTo("dana");
    assertThat(byDana.getRejectionType()).isEqualTo(RejectionType.FORBIDDEN);
  }

  @Test
  public void shouldNotLetAGrantCoverARequestThatSomeoneElseMade() {
    // given Alice granted standing consent, and Dana asks for a change of Alice's Region
    engine.consent().grant(CONNECTION, OPERATION, "alice");

    // when
    final long processInstanceKey = startChange("alice", "Scandinavia", "dana");

    // then no rule admits Dana, so the instance waits for Alice's approval
    assertThat(requestOf(processInstanceKey).getRequester()).isEqualTo("dana");
    assertThat(activate()).isEmpty();
  }

  @Test
  public void shouldHandOutTheValueTheSubjectSaw() {
    // given scenario 8: the value changes after Dana was asked
    final long processInstanceKey = startChange("dana", "Eastern Europe", "dana");
    final long jobKey = requestOf(processInstanceKey).getJobKey();
    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("newRegion", "NA")).update();

    // when
    engine.consent().resolve(jobKey, ConsentDecision.APPROVE_ONCE, "dana");

    // then
    final var job = activate().getFirst();
    assertThat(job.getVariables())
        .containsEntry(ConsentGate.CHANGE_VARIABLE, Map.of("region", "Eastern Europe"));
  }

  @Test
  public void shouldPutTheCredentialPlaceholderOnlyOnAGatedJob() {
    // given
    engine.consent().grant(CONNECTION, OPERATION, "alice");

    // when
    startChange("alice", "DACH", "alice");

    // then the activation record carries the placeholder; the value rides the response only
    final var job = activate().getFirst();
    assertThat(proofOf(job)).containsEntry("credential", "camunda.secrets." + CREDENTIAL);
  }

  @Test
  public void shouldGateARelationshipOnASignedInRequesterAndHandTheCheckToTheWorker() {
    // given scenario 9: Sam has not joined; his manager Dana changes his Region
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("new-hire-region")
                .startEvent()
                .serviceTask(
                    "change",
                    t ->
                        t.zeebeJobType(JOB_TYPE)
                            .zeebeRelationship(CONNECTION, NEW_HIRE_OPERATION, "employeeId")
                            .zeebeRelationshipChangeExpression("{region: newRegion}"))
                .endEvent()
                .done())
        .deploy();

    // when
    engine
        .processInstance()
        .ofBpmnProcessId("new-hire-region")
        .withVariables(Map.of("employeeId", "3332883884017715112", "newRegion", "APAC"))
        .create("dana");

    // then the job is handed out with what the worker needs to check the relationship
    final var proof = proofOf(activate().getFirst());
    assertThat(proof)
        .containsEntry("ground", "RELATIONSHIP")
        .containsEntry("requester", "dana")
        .containsEntry("target", "3332883884017715112")
        .containsEntry("relationship", List.of("subject.work.reportsTo.id"));
  }

  @Test
  public void shouldRaiseAnIncidentForARelationshipWithoutASignedInRequester() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("new-hire-region-by-client")
                .startEvent()
                .serviceTask(
                    "change",
                    t ->
                        t.zeebeJobType(JOB_TYPE)
                            .zeebeRelationship(CONNECTION, NEW_HIRE_OPERATION, "employeeId"))
                .endEvent()
                .done())
        .deploy();

    // when a process starts without a person behind it
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId("new-hire-region-by-client")
            .withVariables(Map.of("employeeId", "3332883884017715112"))
            .create();

    // then
    assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue()
                .getErrorMessage())
        .contains("signed-in person");
  }

  @Test
  public void shouldRejectAnOperationTheConnectionDoesNotDeclare() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("unknown-operation")
                .startEvent()
                .serviceTask(
                    "change",
                    t -> t.zeebeJobType(JOB_TYPE).zeebeConsent(CONNECTION, "salary.update"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("unknown-operation").create("alice");

    // then
    assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue()
                .getErrorMessage())
        .contains("salary.update");
  }

  private static BpmnModelInstance regionChange() {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .serviceTask(
            "change",
            t ->
                t.zeebeJobType(JOB_TYPE)
                    .zeebeConsent(CONNECTION, OPERATION)
                    .zeebeConsentSubjectExpression("employee")
                    .zeebeConsentChangeExpression("{region: newRegion}"))
        .boundaryEvent("declined", b -> b.error("CONSENT_DECLINED"))
        .endEvent("refused")
        .moveToActivity("change")
        .boundaryEvent("timeout", b -> b.timerWithDuration("P14D"))
        .endEvent("no-answer")
        .moveToActivity("change")
        .endEvent("changed")
        .done();
  }

  private long startChange(final String employee, final String region, final String requester) {
    return engine
        .processInstance()
        .ofBpmnProcessId(PROCESS_ID)
        .withVariables(Map.of("employee", employee, "newRegion", region))
        .create(requester);
  }

  private static ConsentRecordValue requestOf(final long processInstanceKey) {
    return (ConsentRecordValue)
        RecordingExporter.records()
            .withValueType(ValueType.CONSENT)
            .withIntent(ConsentIntent.REQUESTED)
            .filter(
                r ->
                    ((ConsentRecordValue) r.getValue()).getProcessInstanceKey()
                        == processInstanceKey)
            .getFirst()
            .getValue();
  }

  private List<JobRecordValue> activate() {
    return engine.jobs().withType(JOB_TYPE).activate().getValue().getJobs();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> proofOf(final JobRecordValue job) {
    return (Map<String, Object>) job.getVariables().get(ConsentGate.CONSENT_VARIABLE);
  }

  private static String endEventOf(final long processInstanceKey) {
    return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.END_EVENT)
        .getFirst()
        .getValue()
        .getElementId();
  }
}

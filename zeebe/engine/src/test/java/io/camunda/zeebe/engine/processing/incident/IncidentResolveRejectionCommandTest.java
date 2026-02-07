/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.incident;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.assertj.core.api.Assertions;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class IncidentResolveRejectionCommandTest {

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withSecurityConfig(
              config -> {
                config.getMultiTenancy().setChecksEnabled(true);
              });

  private static final String TENANT = "custom-tenant";
  private static final String USERNAME = "tenant-user";

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @BeforeClass
  public static void setUp() {
    final var user = ENGINE.user().newUser(USERNAME).create().getValue();
    final var createdUsername = user.getUsername();
    ENGINE.tenant().newTenant().withTenantId(TENANT).create().getValue().getTenantKey();
    ENGINE
        .tenant()
        .addEntity(TENANT)
        .withEntityType(EntityType.USER)
        .withEntityId(createdUsername)
        .add();
  }

  @Test
  public void shouldEnrichRejectionCommandWhenNoRetriesLeft() {
    // given - create a process instance with a job incident (no retries left)
    final String processId = Strings.newRandomValidBpmnId();
    final String jobType = Strings.newRandomValidBpmnId();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(jobType))
                .endEvent()
                .done())
        .withTenantId(TENANT)
        .deploy();

    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withTenantId(TENANT).create();

    // Wait for job creation and activate it
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    ENGINE.jobs().withType(jobType).withTenantId(TENANT).activate(USERNAME);

    // Fail the job with no retries
    final var failedEvent =
        ENGINE.job().withType(jobType).ofInstance(processInstanceKey).withRetries(0).fail(USERNAME);

    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withJobKey(failedEvent.getKey())
            .getFirst();

    // when - try to resolve the incident without updating retries
    final var rejectionRecord =
        ENGINE
            .incident()
            .ofInstance(processInstanceKey)
            .withKey(incidentCreated.getKey())
            .expectRejection()
            .resolve(USERNAME);

    // then - verify the rejection record is enriched
    assertThat(rejectionRecord)
        .hasIntent(IncidentIntent.RESOLVE)
        .hasRejectionType(RejectionType.INVALID_STATE);

    // The elementInstancePath should be set from the incident record
    Assertions.assertThat(rejectionRecord.getValue().getElementInstancePath())
        .describedAs("Rejection record should have elementInstancePath set")
        .isNotEmpty();

    Assertions.assertThat(rejectionRecord.getValue().getTenantId())
        .describedAs("Rejection record should have tenantId set")
        .isEqualTo(TENANT);
  }

  @Test
  public void shouldEnrichRejectionCommandForChildProcess() {
    // given - parent process with call activity that creates an incident
    final String parentProcessId = Strings.newRandomValidBpmnId();
    final String childProcessId = Strings.newRandomValidBpmnId();
    final String jobType = Strings.newRandomValidBpmnId();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(childProcessId)
                .startEvent()
                .serviceTask("childTask", t -> t.zeebeJobType(jobType))
                .endEvent()
                .done())
        .withTenantId(TENANT)
        .deploy();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(parentProcessId)
                .startEvent()
                .callActivity("callActivity", ca -> ca.zeebeProcessId(childProcessId))
                .endEvent()
                .done())
        .withTenantId(TENANT)
        .deploy();

    final var parentProcessInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(parentProcessId).withTenantId(TENANT).create();

    // Wait for the child process instance to be created
    final var childProcessInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(parentProcessInstanceKey)
            .withBpmnProcessId(childProcessId)
            .getFirst();

    final var childProcessInstanceKey = childProcessInstance.getValue().getProcessInstanceKey();

    // Wait for job creation in child process and activate it
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(childProcessInstanceKey)
        .await();
    ENGINE.jobs().withType(jobType).withTenantId(TENANT).activate(USERNAME);

    // Fail the job with no retries
    ENGINE
        .job()
        .withType(jobType)
        .ofInstance(childProcessInstanceKey)
        .withRetries(0)
        .fail(USERNAME);

    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(childProcessInstanceKey)
            .getFirst();

    // when - try to resolve the incident without updating retries
    final var rejectionRecord =
        ENGINE
            .incident()
            .ofInstance(childProcessInstanceKey)
            .withKey(incidentCreated.getKey())
            .expectRejection()
            .resolve(USERNAME);

    // then - verify the rejection record has elementInstancePath with call hierarchy
    assertThat(rejectionRecord)
        .hasIntent(IncidentIntent.RESOLVE)
        .hasRejectionType(RejectionType.INVALID_STATE);

    // The elementInstancePath should contain the call hierarchy from the incident
    final var elementInstancePath = rejectionRecord.getValue().getElementInstancePath();
    Assertions.assertThat(elementInstancePath)
        .describedAs("Rejection record should have elementInstancePath set with call hierarchy")
        .isNotEmpty();

    // There should be multiple levels in the path (parent and child)
    Assertions.assertThat(elementInstancePath.size())
        .describedAs("Element instance path should contain multiple levels for nested process")
        .isGreaterThan(1);

    Assertions.assertThat(rejectionRecord.getValue().getTenantId())
        .describedAs("Rejection record should have tenantId set")
        .isEqualTo(TENANT);
  }

  @Test
  public void shouldNotEnrichRejectionWhenIncidentNotFound() {
    // given - create a valid process instance first to work with the engine
    final String processId = Strings.newRandomValidBpmnId();
    final String jobType = Strings.newRandomValidBpmnId();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(jobType))
                .endEvent()
                .done())
        .withTenantId(TENANT)
        .deploy();

    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withTenantId(TENANT).create();

    // Wait for the process to start
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("task")
        .await();

    // Create an incident first so we have a valid key range
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    ENGINE.jobs().withType(jobType).withTenantId(TENANT).activate(USERNAME);
    ENGINE.job().withType(jobType).ofInstance(processInstanceKey).withRetries(0).fail(USERNAME);

    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // Resolve the incident first by updating retries
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(jobType)
        .withRetries(1)
        .updateRetries(USERNAME);
    ENGINE
        .incident()
        .ofInstance(processInstanceKey)
        .withKey(incidentCreated.getKey())
        .resolve(USERNAME);

    // Wait for incident to be resolved
    RecordingExporter.incidentRecords(IncidentIntent.RESOLVED)
        .withProcessInstanceKey(processInstanceKey)
        .withRecordKey(incidentCreated.getKey())
        .await();

    // when - try to resolve the already resolved incident again (NOT_FOUND scenario)
    final var rejectionRecord =
        ENGINE
            .incident()
            .ofInstance(processInstanceKey)
            .withKey(incidentCreated.getKey())
            .expectRejection()
            .resolve(USERNAME);

    // then - the rejection should still be written but without enrichment
    // (since the incident doesn't exist anymore, we can't look up elementInstancePath)
    assertThat(rejectionRecord)
        .hasIntent(IncidentIntent.RESOLVE)
        .hasRejectionType(RejectionType.NOT_FOUND);

    // elementInstancePath should be empty (default) since we couldn't look it up
    Assertions.assertThat(rejectionRecord.getValue().getElementInstancePath())
        .describedAs(
            "Rejection record should have empty elementInstancePath when incident not found")
        .isEmpty();
  }
}

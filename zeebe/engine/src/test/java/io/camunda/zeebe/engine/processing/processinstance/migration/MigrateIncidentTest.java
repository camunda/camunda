/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance.migration;

import static io.camunda.zeebe.engine.processing.processinstance.migration.MigrationTestUtil.extractProcessDefinitionKeyByProcessId;
import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class MigrateIncidentTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldWriteMigratedEventWhenActiveElementHasAJobIncident() {
    // given
    final String sourceProcessId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(sourceProcessId)
                    .startEvent()
                    .serviceTask("A", t -> t.zeebeJobType("jobTypeA"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .serviceTask("B", t -> t.zeebeJobType("jobTypeB"))
                    .endEvent("target_process_end")
                    .done())
            .deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(sourceProcessId).create();

    final Record<JobRecordValue> failedEvent =
        ENGINE.job().withType("jobTypeA").ofInstance(processInstanceKey).withRetries(0).fail();

    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withJobKey(failedEvent.getKey())
            .getFirst();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.MIGRATED)
                .withRecordKey(incident.getKey())
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that element id changed due to mapping")
        .hasElementId("B")
        .describedAs("Expect that other properties of the incident did not change")
        .hasErrorType(incident.getValue().getErrorType())
        .hasErrorMessage(incident.getValue().getErrorMessage())
        .hasProcessInstanceKey(incident.getValue().getProcessInstanceKey())
        .hasElementInstanceKey(incident.getValue().getElementInstanceKey())
        .hasJobKey(incident.getValue().getJobKey())
        .hasVariableScopeKey(incident.getValue().getVariableScopeKey())
        .hasTenantId(incident.getValue().getTenantId())
        .describedAs("Expect that the process definition path is updated")
        .hasOnlyProcessDefinitionPath(targetProcessDefinitionKey);

    // after resolving the incident, job can be completed and the process should continue
    ENGINE.job().ofInstance(processInstanceKey).withType("jobTypeA").withRetries(1).updateRetries();
    final Record<IncidentRecordValue> incidentRecord =
        ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    assertThat(incidentRecord.getValue())
        .describedAs("Expect that the incident resolved event contains updated fields")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("B");

    // note that re-evaluation of the job type expression is not enabled for this migration
    // so the jobType didn't change
    ENGINE.job().ofInstance(processInstanceKey).withType("jobTypeA").complete();

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.END_EVENT)
                .withElementId("target_process_end")
                .findAny())
        .describedAs("Expect that the process instance is continued in the target process")
        .isPresent();
  }

  @Test
  public void shouldWriteMigratedEventWhenActiveElementHasAProcessIncident() {
    // given
    final String sourceProcessId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(sourceProcessId)
                    .startEvent()
                    .serviceTask(
                        "A",
                        b ->
                            b.zeebeJobType("jobTypeA")
                                .zeebeInputExpression("assert(x, x != null)", "y"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .serviceTask("B", t -> t.zeebeJobType("jobTypeB"))
                    .endEvent("target_process_end")
                    .done())
            .deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(sourceProcessId).create();

    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that element id changed due to mapping")
        .hasElementId("B")
        .describedAs("Expect that other properties of the incident did not change")
        .hasErrorType(incident.getValue().getErrorType())
        .hasErrorMessage(incident.getValue().getErrorMessage())
        .hasProcessInstanceKey(incident.getValue().getProcessInstanceKey())
        .hasElementInstanceKey(incident.getValue().getElementInstanceKey())
        .hasJobKey(incident.getValue().getJobKey())
        .hasVariableScopeKey(incident.getValue().getVariableScopeKey())
        .hasTenantId(incident.getValue().getTenantId())
        .describedAs("Expect that the process definition path is updated")
        .hasOnlyProcessDefinitionPath(targetProcessDefinitionKey);

    // after resolving the incident, the process should continue as expected
    final Record<IncidentRecordValue> incidentRecord =
        ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    assertThat(incidentRecord.getValue())
        .describedAs("Expect that the incident resolved event contains updated fields")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("B");

    // since the job wasn't created in the source process before the migration, a new job is created
    // in the target process after resolving the incident
    Assertions.assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("B") // target element id
                .exists())
        .describedAs("Expect that the job is created in the migrated process")
        .isTrue();

    ENGINE.job().ofInstance(processInstanceKey).withType("jobTypeB").complete();

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.END_EVENT)
                .withElementId("target_process_end")
                .findAny())
        .describedAs("Expect that the process instance is continued in the target process")
        .isPresent();
  }
}

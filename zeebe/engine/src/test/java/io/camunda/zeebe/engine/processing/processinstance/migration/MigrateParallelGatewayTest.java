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

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class MigrateParallelGatewayTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldMigrateParallelGatewayWithIncident() {
    final String sourceProcessId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "_target";

    final String nonExistingElJobTypeExp = "=source_execution_listener_job_type";
    final String existingElJobTypeExp = "=target_execution_listener_job_type";
    final String existingElJobTypeVar = "target_execution_listener_job_type";
    final String existingElJobTypeValue = "elJobType";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(sourceProcessId)
                    .startEvent()
                    .parallelGateway("parallel1")
                    .zeebeStartExecutionListener(nonExistingElJobTypeExp)
                    .endEvent("end1")
                    .moveToLastGateway()
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .parallelGateway("parallel2")
                    .zeebeStartExecutionListener(existingElJobTypeExp)
                    .endEvent("end2")
                    .moveToLastGateway()
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(sourceProcessId)
            .withVariable(existingElJobTypeVar, existingElJobTypeValue)
            .create();

    final var incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("parallel1")
            .getFirst();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("parallel1", "parallel2")
        .migrate();

    RecordingExporter.incidentRecords(IncidentIntent.MIGRATED)
        .withRecordKey(incident.getKey())
        .await();

    // then
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();
    ENGINE.job().ofInstance(processInstanceKey).withType(existingElJobTypeValue).complete();

    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .processInstanceRecords()
                .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.END_EVENT))
        .extracting(r -> r.getValue().getElementId())
        .describedAs(
            "Expected to successfully evaluate execution listener job type and resolve incident")
        .contains("end2");
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.resource.ResourceDeletionRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceDeletionIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;

/**
 * Reproduces a race between creating an instance and deleting its process definition. Follow-up
 * commands are processed in the same batch as their command, unless the batch limit is reached; a
 * batch limit of one forces the ACTIVATE_ELEMENT of a new instance to be written to the log and
 * processed after a deletion that is already on the log.
 */
public class DrainingProcessDefinitionRaceTest {

  private static final String JOB_TYPE = "task";

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule public final EngineRule engine = EngineRule.singlePartition().maxCommandsInBatch(1);

  @Test
  public void shouldRunInstanceCreatedRightBeforeTheDefinitionIsDeleted() {
    // given - a definition with a running instance, so a deletion leaves it draining
    final var processId = helper.getBpmnProcessId();
    final long processDefinitionKey =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
                    .endEvent()
                    .done())
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .getFirst()
            .getProcessDefinitionKey();
    final long runningInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(runningInstanceKey)
        .await();

    // when - a create and a delete are on the log back to back, so the ACTIVATE_ELEMENT of the new
    // instance is processed after the deletion
    engine.writeRecords(
        RecordToWrite.command()
            .processInstanceCreation(
                ProcessInstanceCreationIntent.CREATE,
                new ProcessInstanceCreationRecord().setProcessDefinitionKey(processDefinitionKey)),
        RecordToWrite.command()
            .resourceDeletion(
                ResourceDeletionIntent.DELETE,
                new ResourceDeletionRecord().setResourceKey(processDefinitionKey)));

    // then - the create was accepted, so the created instance runs
    final long createdInstanceKey =
        RecordingExporter.processInstanceCreationRecords()
            .withIntent(ProcessInstanceCreationIntent.CREATED)
            .valueFilter(
                v ->
                    v.getProcessDefinitionKey() == processDefinitionKey
                        && v.getProcessInstanceKey() != runningInstanceKey)
            .getFirst()
            .getValue()
            .getProcessInstanceKey();
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(createdInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .describedAs("an instance whose creation was accepted must be activated")
        .isTrue();
  }
}

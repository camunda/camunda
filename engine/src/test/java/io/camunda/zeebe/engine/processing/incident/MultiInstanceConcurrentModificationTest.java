/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.engine.util.Records;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class MultiInstanceConcurrentModificationTest {

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          // Disable batch processing. Concurrent modification is only reproducible if
          // we do not process multi instance in batch.
          .maxCommandsInBatch(1);

  private static final String MULTI_TASK_PROCESS = "multi-task-process";
  private static final String ELEMENT_ID = "task";
  private static final String INPUT_COLLECTION = "items";
  private static final String INPUT_ELEMENT = "item";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();
  private String jobType;

  @Before
  public void init() {
    jobType = helper.getJobType();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(MULTI_TASK_PROCESS)
                .startEvent()
                .serviceTask(
                    ELEMENT_ID,
                    t ->
                        t.zeebeJobType(jobType)
                            .multiInstance(
                                b ->
                                    b.zeebeInputCollectionExpression(INPUT_COLLECTION)
                                        .zeebeInputElement(INPUT_ELEMENT)
                                        .zeebeOutputElementExpression("{x: undefined_var}")
                                        .zeebeOutputCollection("results")))
                .endEvent()
                .done())
        .deploy();
  }

  /**
   * This test is a bit more complex then shouldResolveIncidentDueToInputCollection, because it
   * tests a parallel multi-instance body that is about to activate, but while it's activating (and
   * before it's children activate) the input collection is modified. This should result in
   * incidents on each of the children's activations, which can be resolved individually.
   */
  @Test
  public void shouldCreateIncidentWhenInputCollectionModifiedConcurrently() {
    // given
    final var process =
        Bpmn.createExecutableProcess("multi-task")
            .startEvent()
            .serviceTask(ELEMENT_ID, t -> t.zeebeJobType(jobType))
            .sequenceFlowId("from-task-to-multi-instance")
            .serviceTask("multi-instance", t -> t.zeebeJobType(jobType))
            .multiInstance(
                b ->
                    b.parallel()
                        .zeebeInputCollectionExpression(INPUT_COLLECTION)
                        .zeebeInputElement(INPUT_ELEMENT))
            .endEvent()
            .done();
    final var deployment = ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId("multi-task")
            .withVariable(INPUT_COLLECTION, List.of(1, 2, 3))
            .create();
    final var serviceTask =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst();
    final var job = findNthJob(processInstanceKey, 1);
    ENGINE.stop();
    RecordingExporter.reset();

    // when
    final ProcessInstanceRecord sequenceFlow =
        Records.processInstance(processInstanceKey, "multi-task")
            .setBpmnElementType(BpmnElementType.SEQUENCE_FLOW)
            .setElementId("from-task-to-multi-instance")
            .setFlowScopeKey(processInstanceKey)
            .setProcessDefinitionKey(
                deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey());
    final ProcessInstanceRecord multiInstanceBody =
        Records.processInstance(processInstanceKey, "multi-task")
            .setBpmnElementType(BpmnElementType.MULTI_INSTANCE_BODY)
            .setElementId("multi-instance")
            .setFlowScopeKey(processInstanceKey)
            .setProcessDefinitionKey(
                deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey());

    ENGINE.writeRecords(
        RecordToWrite.command().key(job.getKey()).job(JobIntent.COMPLETE, job.getValue()),
        RecordToWrite.event()
            .causedBy(0)
            .key(job.getKey())
            .job(JobIntent.COMPLETED, job.getValue()),
        RecordToWrite.command()
            .causedBy(0)
            .key(serviceTask.getKey())
            .processInstance(ProcessInstanceIntent.COMPLETE_ELEMENT, serviceTask.getValue()),
        RecordToWrite.event()
            .causedBy(2)
            .key(serviceTask.getKey())
            .processInstance(ProcessInstanceIntent.ELEMENT_COMPLETING, serviceTask.getValue()),
        RecordToWrite.event()
            .causedBy(2)
            .key(serviceTask.getKey())
            .processInstance(ProcessInstanceIntent.ELEMENT_COMPLETED, serviceTask.getValue()),
        RecordToWrite.event()
            .causedBy(2)
            .key(-1L)
            .processInstance(ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN, sequenceFlow),
        RecordToWrite.command()
            .causedBy(2)
            .processInstance(ProcessInstanceIntent.ACTIVATE_ELEMENT, multiInstanceBody),
        RecordToWrite.command()
            .variable(
                VariableDocumentIntent.UPDATE,
                Records.variableDocument(processInstanceKey, "{\"items\":0}")));
    ENGINE.start();

    // then
    final var incidents =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .limit(3)
            .asList();
    assertThat(incidents)
        .describedAs(
            "Should create incident for each child when input element cannot be retrieved from input collection")
        .extracting(Record::getValue)
        .extracting(
            IncidentRecordValue::getElementId,
            IncidentRecordValue::getErrorType,
            IncidentRecordValue::getErrorMessage)
        .containsOnly(
            tuple(
                "multi-instance",
                ErrorType.EXTRACT_VALUE_ERROR,
                "Expected result of the expression 'items' to be 'ARRAY', but was 'NUMBER'."));

    ENGINE
        .variables()
        .ofScope(processInstanceKey)
        .withDocument(Collections.singletonMap(INPUT_COLLECTION, List.of(1, 2, 3)))
        .update();

    incidents.forEach(
        i -> ENGINE.incident().ofInstance(processInstanceKey).withKey(i.getKey()).resolve());

    completeNthJob(processInstanceKey, 2);
    completeNthJob(processInstanceKey, 3);
    completeNthJob(processInstanceKey, 4);

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();
  }

  private static void completeNthJob(final long processInstanceKey, final int n) {
    final var nthJob = findNthJob(processInstanceKey, n);
    ENGINE.job().withKey(nthJob.getKey()).complete();
  }

  private static Record<JobRecordValue> findNthJob(final long processInstanceKey, final int n) {
    return RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .skip(n - 1)
        .getFirst();
  }
}

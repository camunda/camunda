/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static io.camunda.zeebe.model.bpmn.impl.ZeebeConstants.AD_HOC_SUB_PROCESS_ELEMENTS;
import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATED;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_COMPLETED;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_TERMINATED;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AdHocSubProcessBuilder;
import io.camunda.zeebe.model.bpmn.impl.ZeebeConstants;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.adhocsubprocess.AdHocSubProcessInstructionRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResult;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResultActivateElement;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordAssert;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AdHocSubProcessInstructionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.agrona.concurrent.UnsafeBuffer;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class JobBasedAdHocSubProcessTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @ClassRule
  // executes follow up commands immediately
  public static final EngineRule ENGINE_BATCH_COMMAND_1 =
      EngineRule.singlePartition().maxCommandsInBatch(1);

  private static final String PROCESS_ID = "process";
  private static final String AHSP_ELEMENT_ID = "ad-hoc";
  private static final String AHSP_INNER_ELEMENT_ID =
      "ad-hoc" + ZeebeConstants.AD_HOC_SUB_PROCESS_INNER_INSTANCE_ID_POSTFIX;
  private static final String MAIN_END_EVENT_ID = "main-end";

  private static final String ERROR_CODE = "error-code";
  private static final String ERROR_MESSAGE = "BPMN error thrown from job worker";
  private static final String ERROR_BOUNDARY_EVENT_ELEMENT_ID = "error-boundary";
  private static final String ERROR_END_EVENT_ID = "error-end";

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  private BpmnModelInstance process(
      final String jobType, final Consumer<AdHocSubProcessBuilder> modifier) {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .adHocSubProcess(AHSP_ELEMENT_ID, modifier)
        .zeebeJobType(jobType)
        .boundaryEvent(ERROR_BOUNDARY_EVENT_ELEMENT_ID, b -> b.error(ERROR_CODE))
        .endEvent(ERROR_END_EVENT_ID)
        .moveToActivity(AHSP_ELEMENT_ID)
        .endEvent(MAIN_END_EVENT_ID)
        .done();
  }

  @Test
  public void shouldDeployProcess() {
    // given
    final BpmnModelInstance process =
        process(
            UUID.randomUUID().toString(),
            adHocSubProcess -> {
              adHocSubProcess.task("A1").task("A2");
              adHocSubProcess.task("B");
            });

    // when
    final Record<DeploymentRecordValue> deploymentEvent =
        ENGINE.deployment().withXmlResource(process).deploy();

    // then
    assertThat(deploymentEvent).hasRecordType(RecordType.EVENT).hasIntent(DeploymentIntent.CREATED);
  }

  @Test
  public void shouldActivateAdHocSubProcess() {
    // given
    final BpmnModelInstance process =
        process(UUID.randomUUID().toString(), adHocSubProcess -> adHocSubProcess.task("A"));

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(AHSP_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED))
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSequence(
            tuple(BpmnElementType.AD_HOC_SUB_PROCESS, ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(BpmnElementType.AD_HOC_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.AD_HOC_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED));

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(AHSP_ELEMENT_ID)
                .getFirst()
                .getValue())
        .hasElementId(AHSP_ELEMENT_ID)
        .hasBpmnElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
        .hasBpmnEventType(BpmnEventType.UNSPECIFIED)
        .hasFlowScopeKey(processInstanceKey);
  }

  @Test
  public void shouldCreateJobOnActivation() {
    // given
    final var jobType = UUID.randomUUID().toString();
    final BpmnModelInstance process =
        process(jobType, adHocSubProcess -> adHocSubProcess.task("A1"));
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final var adHocSubProcess =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
            .withElementId(AHSP_ELEMENT_ID)
            .getFirst();

    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(AHSP_ELEMENT_ID)
                .getFirst()
                .getValue())
        .hasType(jobType)
        .hasRetries(3)
        .hasElementInstanceKey(adHocSubProcess.getKey())
        .hasElementId(adHocSubProcess.getValue().getElementId())
        .hasProcessDefinitionKey(adHocSubProcess.getValue().getProcessDefinitionKey())
        .hasBpmnProcessId(adHocSubProcess.getValue().getBpmnProcessId())
        .hasProcessDefinitionVersion(adHocSubProcess.getValue().getVersion());
  }

  @Test
  public void shouldRecreateJobOnInnerInstanceCompletion() {
    // given
    final var jobType = UUID.randomUUID().toString();
    final BpmnModelInstance process =
        process(jobType, adHocSubProcess -> adHocSubProcess.task("A1").task("A2"));
    ENGINE.deployment().withXmlResource(process).deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    completeJob(jobType, false, false, activateElement("A1"));

    // then
    final var adHocSubProcess =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
            .withElementId(AHSP_ELEMENT_ID)
            .getFirst();

    Assertions.assertThat(
            RecordingExporter.jobRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(AHSP_ELEMENT_ID)
                .limit(3))
        .extracting(Record::getIntent, job -> job.getValue().getElementInstanceKey())
        .containsSequence(
            tuple(JobIntent.CREATED, adHocSubProcess.getKey()),
            tuple(JobIntent.COMPLETED, adHocSubProcess.getKey()),
            tuple(JobIntent.CREATED, adHocSubProcess.getKey()));
  }

  @Test
  public void shouldCancelExistingJobsBeforeRecreatingOnInnerInstanceCompletion() {
    // given
    final var jobType = UUID.randomUUID().toString();
    final BpmnModelInstance process =
        process(
            jobType,
            adHocSubProcess -> {
              adHocSubProcess.task("A");
              adHocSubProcess.task("B");
            });
    ENGINE.deployment().withXmlResource(process).deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    completeJob(jobType, false, false, activateElement("A"), activateElement("B"));

    // then
    final var adHocSubProcess =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
            .withElementId(AHSP_ELEMENT_ID)
            .getFirst();

    Assertions.assertThat(
            RecordingExporter.jobRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(AHSP_ELEMENT_ID)
                .limit(5))
        .extracting(Record::getIntent, job -> job.getValue().getElementInstanceKey())
        .containsSequence(
            tuple(JobIntent.CREATED, adHocSubProcess.getKey()),
            tuple(JobIntent.COMPLETED, adHocSubProcess.getKey()),
            tuple(JobIntent.CREATED, adHocSubProcess.getKey()),
            tuple(JobIntent.CANCELED, adHocSubProcess.getKey()),
            tuple(JobIntent.CREATED, adHocSubProcess.getKey()));
  }

  @Test
  public void shouldActivateSecondSetOfElements() {
    // given
    final var jobType = UUID.randomUUID().toString();
    final BpmnModelInstance process =
        process(
            jobType,
            adHocSubProcess -> {
              adHocSubProcess.task("A");
              adHocSubProcess.task("B");
              adHocSubProcess.task("C");
            });
    ENGINE.deployment().withXmlResource(process).deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    completeJob(jobType, false, false, activateElement("A"), activateElement("B"));

    // when
    completeJob(jobType, false, false, activateElement("C"));

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitByCount(
                    r ->
                        r.getIntent() == ELEMENT_COMPLETED
                            && r.getValue().getElementId().equals(AHSP_INNER_ELEMENT_ID),
                    3))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple(AHSP_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                AHSP_INNER_ELEMENT_ID,
                ProcessInstanceIntent.ELEMENT_ACTIVATED), // inner instance for A
            tuple(
                AHSP_INNER_ELEMENT_ID,
                ProcessInstanceIntent.ELEMENT_ACTIVATED), // inner instance for B
            tuple("A", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("B", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("A", ELEMENT_COMPLETED),
            tuple("B", ELEMENT_COMPLETED),
            tuple(AHSP_INNER_ELEMENT_ID, ELEMENT_COMPLETED),
            tuple(AHSP_INNER_ELEMENT_ID, ELEMENT_COMPLETED),
            tuple(
                AHSP_INNER_ELEMENT_ID,
                ProcessInstanceIntent.ELEMENT_ACTIVATED), // inner instance for C
            tuple("C", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("C", ELEMENT_COMPLETED),
            tuple(AHSP_INNER_ELEMENT_ID, ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCancelJobOnTermination() {
    // given
    final var jobType = UUID.randomUUID().toString();
    final BpmnModelInstance process =
        process(jobType, adHocSubProcess -> adHocSubProcess.task("A1"));
    ENGINE.deployment().withXmlResource(process).deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    final var adHocSubProcess =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
            .withElementId(AHSP_ELEMENT_ID)
            .getFirst();

    assertThat(
            RecordingExporter.jobRecords(JobIntent.CANCELED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(AHSP_ELEMENT_ID)
                .getFirst()
                .getValue())
        .hasType(jobType)
        .hasRetries(3)
        .hasElementInstanceKey(adHocSubProcess.getKey())
        .hasElementId(adHocSubProcess.getValue().getElementId())
        .hasProcessDefinitionKey(adHocSubProcess.getValue().getProcessDefinitionKey())
        .hasBpmnProcessId(adHocSubProcess.getValue().getBpmnProcessId())
        .hasProcessDefinitionVersion(adHocSubProcess.getValue().getVersion());
  }

  @Test
  public void shouldCreateIncidentOnJobFail() {
    // given
    final var jobType = UUID.randomUUID().toString();
    final BpmnModelInstance process =
        process(jobType, adHocSubProcess -> adHocSubProcess.task("A1"));
    ENGINE.deployment().withXmlResource(process).deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    final var jobKey =
        ENGINE.jobs().withType(jobType).activate().getValue().getJobKeys().getFirst();
    ENGINE
        .job()
        .withKey(jobKey)
        .withRetries(0)
        .withErrorCode("errorCode")
        .withErrorMessage("jobFailed")
        .fail();

    // then
    final var adHocSubProcess =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
            .withElementId(AHSP_ELEMENT_ID)
            .getFirst();

    assertThat(
            RecordingExporter.jobRecords(JobIntent.FAILED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(AHSP_ELEMENT_ID)
                .getFirst()
                .getValue())
        .hasType(jobType)
        .hasElementInstanceKey(adHocSubProcess.getKey())
        .hasElementId(adHocSubProcess.getValue().getElementId())
        .hasProcessDefinitionKey(adHocSubProcess.getValue().getProcessDefinitionKey())
        .hasBpmnProcessId(adHocSubProcess.getValue().getBpmnProcessId())
        .hasProcessDefinitionVersion(adHocSubProcess.getValue().getVersion());

    assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .hasElementId(AHSP_ELEMENT_ID)
        .hasElementInstanceKey(adHocSubProcess.getKey())
        .hasErrorType(ErrorType.AD_HOC_SUB_PROCESS_NO_RETRIES)
        .hasErrorMessage("jobFailed");
  }

  @Test
  public void shouldThrowAndPropagateBpmnError() {
    // given
    final var jobType = UUID.randomUUID().toString();
    final BpmnModelInstance process =
        process(jobType, adHocSubProcess -> adHocSubProcess.task("A1"));
    ENGINE.deployment().withXmlResource(process).deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    final var jobKey =
        ENGINE.jobs().withType(jobType).activate().getValue().getJobKeys().getFirst();
    ENGINE
        .job()
        .withKey(jobKey)
        .withRetries(0)
        .withErrorCode(ERROR_CODE)
        .withErrorMessage(ERROR_MESSAGE)
        .throwError();

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId())
        .containsSubsequence(ERROR_BOUNDARY_EVENT_ELEMENT_ID, ERROR_END_EVENT_ID, PROCESS_ID);
  }

  @Test
  public void shouldActivateElementsOnJobCompletion() {
    // given
    final var jobType = UUID.randomUUID().toString();
    final BpmnModelInstance process =
        process(
            jobType,
            adHocSubProcess -> {
              adHocSubProcess.task("A1").task("A2");
              adHocSubProcess.task("B");
              adHocSubProcess.task("C");
            });
    ENGINE.deployment().withXmlResource(process).deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    completeJob(jobType, false, false, activateElement("A1"), activateElement("B"));

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitByCount(
                    r ->
                        r.getIntent() == ELEMENT_COMPLETED
                            && r.getValue().getElementId().equals(AHSP_INNER_ELEMENT_ID),
                    2))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple(AHSP_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(AHSP_INNER_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(AHSP_INNER_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("A1", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("B", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("A1", ELEMENT_COMPLETED),
            tuple("B", ELEMENT_COMPLETED),
            tuple("A2", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(AHSP_INNER_ELEMENT_ID, ELEMENT_COMPLETED), // inner instance for B
            tuple("A2", ELEMENT_COMPLETED),
            tuple(AHSP_INNER_ELEMENT_ID, ELEMENT_COMPLETED)) // inner instance for A
        .doesNotContain(tuple("C", ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldCreateVariablesOnActivatedElementScope() {
    // given
    final var jobType = UUID.randomUUID().toString();
    final BpmnModelInstance process =
        process(
            jobType,
            adHocSubProcess -> {
              adHocSubProcess.task("A");
              adHocSubProcess.task("B");
            });
    ENGINE.deployment().withXmlResource(process).deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    completeJob(
        jobType,
        false,
        false,
        activateElement("A", Map.of("foo", "bar", "baz", 10)),
        activateElement("B"));

    final var elementA =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("A")
            .getFirst()
            .getValue();
    final var elementB =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("B")
            .getFirst()
            .getValue();

    ENGINE.signal().withSignalName("signal").broadcast();

    Assertions.assertThat(
            RecordingExporter.records()
                .limit(r -> r.getIntent() == SignalIntent.BROADCASTED)
                .variableRecords()
                .withProcessInstanceKey(processInstanceKey)
                .filter(v -> !v.getValue().getName().equals(AD_HOC_SUB_PROCESS_ELEMENTS)))
        .extracting(
            r -> r.getValue().getName(),
            r -> r.getValue().getValue(),
            r -> r.getValue().getScopeKey())
        .containsOnly(
            tuple("foo", "\"bar\"", elementA.getFlowScopeKey()),
            tuple("baz", "10", elementA.getFlowScopeKey()))
        .doesNotContain(
            tuple("foo", "\"bar\"", elementB.getFlowScopeKey()),
            tuple("baz", "10", elementB.getFlowScopeKey()));
  }

  @Test
  public void shouldNotActivateElementsThatDoNotExistAndReject() {
    // given
    final var jobType = UUID.randomUUID().toString();
    final BpmnModelInstance process =
        process(jobType, adHocSubProcess -> adHocSubProcess.task("A"));
    ENGINE.deployment().withXmlResource(process).deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    completeJob(jobType, false, false, activateElement("DoesntExist"), activateElement("NotThere"));

    final var ahspKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(AHSP_ELEMENT_ID)
            .getFirst()
            .getKey();
    Assertions.assertThat(
            RecordingExporter.jobRecords(JobIntent.COMPLETE).onlyCommandRejections().getFirst())
        .extracting(Record::getRejectionType, Record::getRejectionReason)
        .containsOnly(
            RejectionType.NOT_FOUND,
            "Expected to activate activities for ad-hoc sub-process with key '%d', but the given elements [DoesntExist, NotThere] do not exist."
                .formatted(ahspKey));

    Assertions.assertThat(
            RecordingExporter.records()
                .limit(
                    r ->
                        r.getIntent().equals(JobIntent.COMPLETE)
                            && r.getRejectionType().equals(RejectionType.NOT_FOUND))
                .processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .doesNotContain(tuple("A", ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldCompleteAdHocSubProcessWhenCompletionConditionFulfilled() {
    // given
    final var jobType = UUID.randomUUID().toString();
    final BpmnModelInstance process =
        process(jobType, adHocSubProcess -> adHocSubProcess.task("A"));
    ENGINE.deployment().withXmlResource(process).deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    completeJob(jobType, true, false);

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId())
        .containsSubsequence(AHSP_ELEMENT_ID, PROCESS_ID);
  }

  @Test
  public void
      shouldCompleteAdHocSubProcessWhenCompletionConditionFulfilledAfterInnerInstanceCompletes() {
    // given
    final var jobType = UUID.randomUUID().toString();
    final BpmnModelInstance process =
        process(
            jobType,
            adHocSubProcess -> {
              adHocSubProcess.task("A");
              adHocSubProcess.userTask("B").zeebeUserTask();
            });
    ENGINE.deployment().withXmlResource(process).deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    completeJob(jobType, false, false, activateElement("A"), activateElement("B"));
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("A")
                .exists())
        .describedAs("Element A is completed")
        .isTrue();
    completeJob(jobType, true, false); // AHSP doesn't complete yet as B is still active

    // when
    final var userTaskKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();
    ENGINE.userTask().withKey(userTaskKey).complete();

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId())
        .containsSubsequence(
            "A", AHSP_INNER_ELEMENT_ID, "B", AHSP_INNER_ELEMENT_ID, AHSP_ELEMENT_ID, PROCESS_ID);
  }

  @Test
  public void shouldRejectCompleteInstructionIfAdHocSubProcessNotExists() {
    // when
    ENGINE.writeRecords(
        RecordToWrite.command()
            .adHocSubProcessInstruction(
                AdHocSubProcessInstructionIntent.COMPLETE,
                new AdHocSubProcessInstructionRecord().setAdHocSubProcessInstanceKey(123L)));

    // then
    Assertions.assertThat(
            RecordingExporter.adHocSubProcessInstructionRecords()
                .withIntent(AdHocSubProcessInstructionIntent.COMPLETE)
                .onlyCommandRejections()
                .getFirst())
        .extracting(Record::getRejectionType, Record::getRejectionReason)
        .containsOnly(
            RejectionType.NOT_FOUND,
            "Expected to complete ad-hoc sub-process, but no element instance found with key '123'.");
  }

  @Test
  public void shouldRejectCompleteInstructionIfElementInstanceIsNoAdHocSubProcess() {
    // given
    final var jobType = UUID.randomUUID().toString();
    final BpmnModelInstance process =
        process(jobType, adHocSubProcess -> adHocSubProcess.task("A"));
    ENGINE.deployment().withXmlResource(process).deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE.writeRecords(
        RecordToWrite.command()
            .adHocSubProcessInstruction(
                AdHocSubProcessInstructionIntent.COMPLETE,
                new AdHocSubProcessInstructionRecord()
                    .setAdHocSubProcessInstanceKey(processInstanceKey)));

    // then
    Assertions.assertThat(
            RecordingExporter.adHocSubProcessInstructionRecords()
                .withIntent(AdHocSubProcessInstructionIntent.COMPLETE)
                .onlyCommandRejections()
                .getFirst())
        .extracting(Record::getRejectionType, Record::getRejectionReason)
        .containsOnly(
            RejectionType.INVALID_ARGUMENT,
            "Expected to complete ad-hoc sub-process, but element instance with key '%d' is not an ad-hoc sub-process."
                .formatted(processInstanceKey));
  }

  @Test
  public void shouldCreateOutputCollectionInAHSPScope() {
    // given
    final var jobType = UUID.randomUUID().toString();
    createOutputCollectionProcessAndDeploy(jobType);

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    completeJob(jobType, false, false, activateElement("A"), activateElement("B"));

    // then
    final var ahsp =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
            .getFirst();
    io.camunda.zeebe.protocol.record.Assertions.assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName("results")
                .getFirst()
                .getValue())
        .describedAs("Should create output collection in scope of ad-hoc sub-process")
        .hasName("results")
        .hasValue("[]")
        .hasScopeKey(ahsp.getKey());
  }

  @Test
  public void shouldCreateOutputElementsInInnerInstanceScope() {
    // given
    final var jobType = UUID.randomUUID().toString();
    createOutputCollectionProcessAndDeploy(jobType);

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    completeJob(jobType, false, false, activateElement("A"), activateElement("B"));

    // then
    final var innerInstanceKeys = getInnerInstanceKeysForOutputCollectionTest(processInstanceKey);

    Assertions.assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName("result")
                .limit(2))
        .describedAs("Should create output elements in scope of inner instances")
        .map(Record::getValue)
        .extracting(
            VariableRecordValue::getName,
            VariableRecordValue::getValue,
            VariableRecordValue::getScopeKey)
        .containsExactlyInAnyOrder(
            tuple("result", "null", innerInstanceKeys.get(0)),
            tuple("result", "null", innerInstanceKeys.get(1)));
  }

  @Test
  public void shouldUpdateOutputElementsInInnerInstanceScope() {
    // given
    final var jobType = UUID.randomUUID().toString();
    createOutputCollectionProcessAndDeploy(jobType);

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    completeJob(jobType, false, false, activateElement("A"), activateElement("B"));

    final var innerInstanceKeys = getInnerInstanceKeysForOutputCollectionTest(processInstanceKey);

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType("A")
        .withVariable("result", "a")
        .complete();

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType("B")
        .withVariable("result", "b")
        .complete();

    // then
    Assertions.assertThat(
            RecordingExporter.variableRecords(VariableIntent.UPDATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName("result")
                .limit(2))
        .describedAs("Should update output elements in scope of inner instances")
        .map(Record::getValue)
        .extracting(
            VariableRecordValue::getName,
            VariableRecordValue::getValue,
            VariableRecordValue::getScopeKey)
        .containsExactlyInAnyOrder(
            tuple("result", "\"a\"", innerInstanceKeys.get(0)),
            tuple("result", "\"b\"", innerInstanceKeys.get(1)));
  }

  @Test
  public void shouldAppendOutputCollection() {
    // given
    final var jobType = UUID.randomUUID().toString();
    createOutputCollectionProcessAndDeploy(jobType);

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    completeJob(jobType, false, false, activateElement("A"), activateElement("B"));

    final var ahsp =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
            .getFirst();

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType("A")
        .withVariable("result", "a")
        .complete();

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType("B")
        .withVariable("result", "b")
        .complete();

    // then
    Assertions.assertThat(
            RecordingExporter.variableRecords(VariableIntent.UPDATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName("results")
                .limit(2))
        .describedAs("Should have updated the output collection twice")
        .extracting(Record::getValue)
        .extracting(
            VariableRecordValue::getName,
            VariableRecordValue::getValue,
            VariableRecordValue::getScopeKey)
        .containsSequence(
            tuple("results", "[\"a\"]", ahsp.getKey()),
            tuple("results", "[\"a\",\"b\"]", ahsp.getKey()));
  }

  @Test
  public void
      shouldTerminateChildrenAndCompleteAhspIfCompletionConditionFulfilledAndCancelInstancesIsSetToTrue() {
    // given
    final var jobType = UUID.randomUUID().toString();
    final BpmnModelInstance process =
        process(
            jobType,
            adHocSubProcess -> {
              adHocSubProcess.task("A");
              adHocSubProcess.userTask("B");
              adHocSubProcess.userTask("C");
            });
    ENGINE.deployment().withXmlResource(process).deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    completeJob(
        jobType, false, false, activateElement("A"), activateElement("B"), activateElement("C"));

    // when
    completeJob(jobType, true, true);

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("A", ELEMENT_COMPLETED),
            tuple(AHSP_INNER_ELEMENT_ID, ELEMENT_COMPLETED), // inner instance for A
            tuple("B", ELEMENT_TERMINATED),
            tuple(AHSP_INNER_ELEMENT_ID, ELEMENT_TERMINATED), // inner instance for B
            tuple("C", ELEMENT_TERMINATED),
            tuple(AHSP_INNER_ELEMENT_ID, ELEMENT_TERMINATED), // inner instance for C
            tuple(AHSP_ELEMENT_ID, ELEMENT_COMPLETED));
  }

  @Test
  public void shouldTerminateChildrenAndActivateOthersWhenCancelInstancesIsSetToTrue() {
    // given
    final var jobType = UUID.randomUUID().toString();
    final BpmnModelInstance process =
        process(
            jobType,
            adHocSubProcess -> {
              adHocSubProcess.task("A");
              adHocSubProcess.userTask("B");
              adHocSubProcess.userTask("C");
            });
    ENGINE.deployment().withXmlResource(process).deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    completeJob(jobType, false, false, activateElement("A"), activateElement("B"));

    // when
    completeJob(jobType, false, true, activateElement("C"));

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(AHSP_INNER_ELEMENT_ID, ELEMENT_TERMINATED))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("A", ELEMENT_COMPLETED),
            tuple(AHSP_INNER_ELEMENT_ID, ELEMENT_COMPLETED), // inner instance for A
            tuple(AHSP_INNER_ELEMENT_ID, ELEMENT_ACTIVATED), // inner instance for C
            tuple("C", ELEMENT_ACTIVATED),
            tuple("B", ELEMENT_TERMINATED),
            tuple(AHSP_INNER_ELEMENT_ID, ELEMENT_TERMINATED)); // inner instance for B
  }

  @Test
  public void shouldRaiseIncidentIfOutputCollectionIsNotAnArray() {
    // given
    final var jobType = UUID.randomUUID().toString();
    final BpmnModelInstance process =
        process(
            jobType,
            adHocSubProcess -> {
              adHocSubProcess.task("A").zeebeOutputExpression("= 1", "outputElement");
              adHocSubProcess.zeebeOutputCollection("outputCollection");
              adHocSubProcess.zeebeOutputElementExpression("= outputElement");
            });
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    RecordingExporter.jobRecords(JobIntent.CREATED).exists();
    final var jobKey =
        ENGINE.jobs().withType(jobType).activate().getValue().getJobKeys().getFirst();
    final var jobResult =
        new JobResult()
            .setActivateElements(List.of(new JobResultActivateElement().setElementId("A")));
    ENGINE
        .job()
        .withKey(jobKey)
        .withResult(jobResult)
        .withVariable("outputCollection", null)
        .complete();

    assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasElementId(AHSP_INNER_ELEMENT_ID)
        .hasErrorMessage("The output collection has the wrong type. Expected ARRAY but was NIL.");
  }

  @Test
  public void shouldTriggerNonInterruptingEventSubProcess() {
    // given
    final var correlationKey = UUID.randomUUID().toString();
    final var jobType = UUID.randomUUID().toString();
    final BpmnModelInstance process =
        process(
            jobType,
            adHocSubProcess -> {
              adHocSubProcess.serviceTask("A").zeebeJobType("jobType");
              adHocSubProcess
                  .embeddedSubProcess()
                  .eventSubProcess("event_sub_process")
                  .startEvent("event_sub_start")
                  .message(
                      m ->
                          m.name("msg")
                              .zeebeCorrelationKeyExpression("=\"%s\"".formatted(correlationKey)))
                  .interrupting(false)
                  .endEvent("event_sub_end");
            });

    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    completeJob(jobType, false, false, activateElement("A"));

    // when
    ENGINE.message().withName("msg").withCorrelationKey(correlationKey).publish();

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .onlyEvents()
                .withProcessInstanceKey(processInstanceKey)
                .withElementIdIn("event_sub_process", "event_sub_start", "event_sub_end")
                .limit("event_sub_process", ProcessInstanceIntent.ELEMENT_COMPLETED))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsExactly(
            tuple("event_sub_process", ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple("event_sub_process", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("event_sub_start", ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple("event_sub_start", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("event_sub_start", ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple("event_sub_start", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("event_sub_end", ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple("event_sub_end", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("event_sub_end", ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple("event_sub_end", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("event_sub_process", ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple("event_sub_process", ProcessInstanceIntent.ELEMENT_COMPLETED));

    completeJob(jobType, true, true);
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(AHSP_ELEMENT_ID)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldTriggerNonInterruptingEventSubProcessMultipleTimes() {
    // given
    final var correlationKey = UUID.randomUUID().toString();
    final var jobType = UUID.randomUUID().toString();
    final BpmnModelInstance process =
        process(
            jobType,
            adHocSubProcess -> {
              adHocSubProcess.serviceTask("A").zeebeJobType("jobType");
              adHocSubProcess
                  .embeddedSubProcess()
                  .eventSubProcess("event_sub_process")
                  .startEvent("event_sub_start")
                  .message(
                      m ->
                          m.name("msg")
                              .zeebeCorrelationKeyExpression("=\"%s\"".formatted(correlationKey)))
                  .interrupting(false)
                  .endEvent("event_sub_end");
            });

    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    completeJob(jobType, false, false, activateElement("A"));

    // when
    ENGINE.message().withName("msg").withCorrelationKey(correlationKey).publish();
    ENGINE.message().withName("msg").withCorrelationKey(correlationKey).publish();

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(2))
        .hasSize(2);

    completeJob(jobType, true, true);
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(AHSP_ELEMENT_ID)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldUpdateOutputCollectionOnEventSubProcessCompletion() {
    // given
    final var correlationKey = UUID.randomUUID().toString();
    final var jobType = UUID.randomUUID().toString();
    final BpmnModelInstance process =
        process(
            UUID.randomUUID().toString(),
            adHocSubProcess -> {
              adHocSubProcess
                  .zeebeOutputCollection("results")
                  .zeebeOutputElementExpression("result");
              adHocSubProcess.serviceTask("A").zeebeJobType(jobType);
              adHocSubProcess
                  .embeddedSubProcess()
                  .eventSubProcess("event_sub_process")
                  .startEvent("event_sub_start")
                  .message(
                      m ->
                          m.name("msg")
                              .zeebeCorrelationKeyExpression("=\"%s\"".formatted(correlationKey)))
                  .interrupting(false)
                  .endEvent("event_sub_end");
            });

    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE
        .message()
        .withName("msg")
        .withCorrelationKey(correlationKey)
        .withVariables(Map.of("result", "foo"))
        .publish();

    // then
    final var ahspKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
            .getFirst()
            .getKey();
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.UPDATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName("results")
                .getFirst()
                .getValue())
        .extracting(
            VariableRecordValue::getName,
            VariableRecordValue::getValue,
            VariableRecordValue::getScopeKey)
        .containsOnly("results", "[\"foo\"]", ahspKey);
  }

  @Test
  public void shouldRecreateJobAfterEventSubProcessCompletion() {
    // given
    final var jobType = UUID.randomUUID().toString();
    final BpmnModelInstance process =
        process(
            jobType,
            adHocSubProcess -> {
              adHocSubProcess.intermediateCatchEvent("A").signal("signal");
              adHocSubProcess.task("B");
              adHocSubProcess
                  .embeddedSubProcess()
                  .eventSubProcess("event_sub_process")
                  .startEvent("event_sub_start")
                  .message(m -> m.name("msg").zeebeCorrelationKeyExpression("=\"correlationKey\""))
                  .interrupting(false)
                  .endEvent("event_sub_end");
            });

    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    completeJob(jobType, false, false, activateElement("A"), activateElement("B"));

    // when
    ENGINE.message().withName("msg").withCorrelationKey("correlationKey").publish();

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("event_sub_process")
                .exists())
        .isTrue();
    ENGINE.signal().withSignalName("signal").broadcast();
    Assertions.assertThat(
            RecordingExporter.records()
                .limit(
                    // Limit on signal to ensure we've reached the end of processing
                    r ->
                        r.getIntent() == SignalIntent.BROADCASTED
                            && ((SignalRecord) r.getValue()).getSignalName().equals("signal"))
                .jobRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(AHSP_ELEMENT_ID))
        .extracting(Record::getIntent)
        .containsExactly(
            JobIntent.CREATED, // Initial job
            JobIntent.COMPLETED, // Activation of A and B
            JobIntent.CREATED, // Completion of B
            JobIntent.CANCELED, // Completion of event sub process
            JobIntent.CREATED); // Completion of event sub process
  }

  @Test
  public void shouldRejectWhenCompletionConditionIsFulfilledAndActivatingElements() {
    // given
    final var jobType = UUID.randomUUID().toString();
    final BpmnModelInstance process =
        process(
            jobType,
            adHocSubProcess -> {
              adHocSubProcess.task("A");
              adHocSubProcess.userTask("B");
              adHocSubProcess.userTask("C");
            });
    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    completeJob(jobType, true, false, activateElement("A"), activateElement("B"));

    // then
    final var ahspKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(AHSP_ELEMENT_ID)
            .getFirst()
            .getKey();

    Assertions.assertThat(RecordingExporter.jobRecords().onlyCommandRejections().getFirst())
        .extracting(Record::getRejectionType, Record::getIntent, Record::getRejectionReason)
        .containsOnly(
            RejectionType.INVALID_ARGUMENT,
            JobIntent.COMPLETE,
            "No elements can be activated for ad-hoc sub-process with key '%s' because the completion condition is fulfilled."
                .formatted(ahspKey));
  }

  @Test
  public void shouldCompleteAgenticJobWithFollowupMetadata() {
    // given
    final var jobType = JobRecord.IO_CAMUNDA_AI_AGENT_JOB_WORKER_TYPE_PREFIX;
    final BpmnModelInstance process =
        process(jobType, adHocSubProcess -> adHocSubProcess.task("A1").task("A2"));
    ENGINE.deployment().withXmlResource(process).deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    final var jobCompleted = completeJob(jobType, true, Map.of("bar", "foo"));

    // then
    Assertions.assertThat(
            RecordingExporter.records()
                .withSourceRecordPosition(jobCompleted.getSourceRecordPosition())
                .between(
                    l -> l.getIntent().equals(JobIntent.COMPLETED),
                    r ->
                        r.getIntent().equals(ProcessInstanceIntent.ELEMENT_COMPLETED)
                            && r.getKey() == processInstanceKey))
        .isNotEmpty()
        .allMatch(r -> AHSP_ELEMENT_ID.equals(r.getAgent().getElementId()));
  }

  private void completeJob(
      final String jobType,
      final boolean completionConditionFulfilled,
      final boolean cancelRemainingInstances,
      final JobResultActivateElement... activateElements) {
    final var jobKey =
        ENGINE.jobs().withType(jobType).activate().getValue().getJobKeys().getFirst();
    final var jobResult =
        new JobResult()
            .setActivateElements(List.of(activateElements))
            .setCompletionConditionFulfilled(completionConditionFulfilled)
            .setCancelRemainingInstances(cancelRemainingInstances);
    ENGINE.job().withKey(jobKey).withResult(jobResult).complete();
  }

  private Record<JobRecordValue> completeJob(
      final String jobType,
      final boolean completionConditionFulfilled,
      final Map<String, Object> variables) {
    final var jobKey =
        ENGINE.jobs().withType(jobType).activate().getValue().getJobKeys().getFirst();
    final var jobResult =
        new JobResult().setCompletionConditionFulfilled(completionConditionFulfilled);
    return ENGINE.job().withKey(jobKey).withResult(jobResult).withVariables(variables).complete();
  }

  private JobResultActivateElement activateElement(final String elementId) {
    return new JobResultActivateElement().setElementId(elementId);
  }

  private JobResultActivateElement activateElement(final String elementId, final Object variables) {
    return new JobResultActivateElement()
        .setElementId(elementId)
        .setVariables(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(variables)));
  }

  private List<Long> getInnerInstanceKeysForOutputCollectionTest(final long processInstanceKey) {
    return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS_INNER_INSTANCE)
        .limit(2)
        .map(Record::getKey)
        .toList();
  }

  private void createOutputCollectionProcessAndDeploy(final String jobType) {
    final var process =
        process(
            jobType,
            adHocSubProcess -> {
              adHocSubProcess
                  .zeebeOutputCollection("results")
                  .zeebeOutputElementExpression("result");
              adHocSubProcess.serviceTask("A", t -> t.zeebeJobType("A"));
              adHocSubProcess.serviceTask("B", t -> t.zeebeJobType("B"));
              adHocSubProcess.task("C");
            });
    ENGINE.deployment().withXmlResource(process).deploy();
  }

  @Test
  public void shouldRejectAdHocJobCompletionWhenAdHocSubProcessNotActive() {
    // given
    final var jobType = UUID.randomUUID().toString();
    final BpmnModelInstance process =
        process(
            jobType,
            ahsp -> {
              ahsp.serviceTask("task", t -> t.zeebeJobType(jobType));
              ahsp.task("undefinedTask");
            });
    ENGINE_BATCH_COMMAND_1.deployment().withXmlResource(process).deploy();

    final long processInstanceKey =
        ENGINE_BATCH_COMMAND_1.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var ahspInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
            .withElementId(AHSP_ELEMENT_ID)
            .getFirst();

    final var activatedJobs = ENGINE_BATCH_COMMAND_1.jobs().withType(jobType).activate().getValue();
    final long jobKey = activatedJobs.getJobKeys().getFirst();

    final var jobResult =
        new JobResult()
            .setActivateElements(List.of(new JobResultActivateElement().setElementId("task")));
    ENGINE_BATCH_COMMAND_1.job().withKey(jobKey).withResult(jobResult).complete();

    // when

    // use max batch size 1 engine so that the commands are processed in order, because the
    // activation command must be processed before the adhoc subprocess is completely terminated.
    // Without this, the termination and it's follow-up commands would be processed completely
    // before the activation command is processed. That would mean the ahsp would be completely
    // terminated as well, and the rejection would be for a different reason (NOT_FOUND).
    ENGINE_BATCH_COMMAND_1.writeRecords(
        RecordToWrite.command()
            .processInstance(ProcessInstanceIntent.TERMINATE_ELEMENT, ahspInstance.getValue())
            .key(ahspInstance.getKey()),
        RecordToWrite.command()
            .adHocSubProcessInstruction(
                AdHocSubProcessInstructionIntent.ACTIVATE,
                new AdHocSubProcessInstructionRecord()
                    .setAdHocSubProcessInstanceKey(ahspInstance.getKey()))
            .key(ahspInstance.getKey()));

    // then
    RecordAssert.assertThat(
            RecordingExporter.adHocSubProcessInstructionRecords()
                .onlyCommandRejections()
                .withAdHocSubProcessInstanceKey(ahspInstance.getKey())
                .getFirst())
        .describedAs("Expected rejection because ad-hoc sub-process is not active.")
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            "Expected to activate activities for ad-hoc sub-process with key '%s', but it is not active."
                .formatted(ahspInstance.getKey()));
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

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
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeAdHocImplementationType;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.adhocsubprocess.AdHocSubProcessInstructionRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResult;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResultActivateElement;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AdHocSubProcessInstructionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.ErrorType;
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

  private static final String PROCESS_ID = "process";
  private static final String AHSP_ELEMENT_ID = "ad-hoc";
  private static final String AHSP_INNER_ELEMENT_ID =
      "ad-hoc" + ZeebeConstants.AD_HOC_SUB_PROCESS_INNER_INSTANCE_ID_POSTFIX;
  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  private BpmnModelInstance process(
      final String jobType, final Consumer<AdHocSubProcessBuilder> modifier) {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .adHocSubProcess(AHSP_ELEMENT_ID, modifier)
        .zeebeImplementation(ZeebeAdHocImplementationType.JOB_WORKER)
        .zeebeJobType(jobType)
        .endEvent()
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
                .filter(v -> !v.getValue().getName().equals("adHocSubProcessElements")))
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

  private JobResultActivateElement activateElement(final String elementId) {
    return new JobResultActivateElement().setElementId(elementId);
  }

  private JobResultActivateElement activateElement(final String elementId, final Object variables) {
    return new JobResultActivateElement()
        .setElementId(elementId)
        .setVariables(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(variables)));
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.client.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.AdHocSubProcessBuilder;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.ProcessInstanceRecordStream;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

@ZeebeIntegration
public class ActivateAdHocSubprocessActivityTest {

  private static final String AD_HOC_SUB_PROCESS_ELEMENT_ID = "ad-hoc";

  @AutoClose CamundaClient client;

  @TestZeebe
  final TestStandaloneBroker zeebe =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  ZeebeResourcesHelper resourcesHelper;
  private String processId;
  private ProcessInstanceEvent processInstance;

  @BeforeEach
  public void init() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
    resourcesHelper = new ZeebeResourcesHelper(client);
  }

  @Test
  void shouldActivateActivitiesAndCompleteProcess(final TestInfo testInfo) {
    // given
    deployAndStartInstance(
        testInfo,
        adHocSubProcess -> {
          adHocSubProcess.task("A");
          adHocSubProcess.task("B");
          adHocSubProcess.task("C");
        });

    client.newBroadcastSignalCommand().signalName("setup_signal").send().join();

    assertThat(recordsUpToSignal("setup_signal"))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .describedAs("Expect ad-hoc subprocess to be activated")
        .contains(tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED))
        .describedAs("Expect no activities to be activated")
        .doesNotContain(
            tuple("A", ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple("B", ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple("C", ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));

    final var activatedAdHocSubprocess =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstance.getProcessInstanceKey())
            .withElementId(AD_HOC_SUB_PROCESS_ELEMENT_ID)
            .getFirst();

    // when
    client
        .newActivateAdHocSubprocessActivitiesCommand(
            String.valueOf(activatedAdHocSubprocess.getKey()))
        .activateElements("A", "C")
        .send()
        .join();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstance.getProcessInstanceKey())
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .describedAs("Expect activated activities and whole process to be completed")
        .contains(
            tuple("A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("C", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  void shouldActivateActivitiesAndCompleteProcessOnlyWhenCompletionConditionIsMet(
      final TestInfo testInfo) {
    // given
    deployAndStartInstance(
        testInfo,
        adHocSubProcess -> {
          adHocSubProcess.completionCondition("condition");
          adHocSubProcess.task("A");
          adHocSubProcess.task("B");
          adHocSubProcess.serviceTask("ServiceTask", b -> b.zeebeJobType("testType"));
        },
        Map.of("condition", false));

    final var activatedAdHocSubprocess =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstance.getProcessInstanceKey())
            .withElementId(AD_HOC_SUB_PROCESS_ELEMENT_ID)
            .getFirst();

    // when1
    client
        .newActivateAdHocSubprocessActivitiesCommand(
            String.valueOf(activatedAdHocSubprocess.getKey()))
        .activateElements("A", "B")
        .send()
        .join();

    client.newBroadcastSignalCommand().signalName("signal1").send().join();

    // then1
    assertThat(recordsUpToSignal("signal1"))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .describedAs(
            "Expect ad-hoc process instance not to be completed until completion condition is not met")
        .contains(
            tuple("A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("B", ProcessInstanceIntent.ELEMENT_COMPLETED))
        .doesNotContain(
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));

    // when2
    client
        .newActivateAdHocSubprocessActivitiesCommand(
            String.valueOf(activatedAdHocSubprocess.getKey()))
        .activateElements("ServiceTask")
        .send()
        .join();

    final var createdJob =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstance.getProcessInstanceKey())
            .getFirst();

    // complete service task with completion condition variable changing to true
    client.newCompleteCommand(createdJob.getKey()).variable("condition", true).send().join();

    // then2
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstance.getProcessInstanceKey())
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .describedAs("Expect activated activities and whole process to be completed")
        .contains(
            tuple("ServiceTask", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  void shouldCancelRemainingInstancesWhenCompletionConditionIsMetAfterActivatingActivities(
      final TestInfo testInfo) {
    // given
    deployAndStartInstance(
        testInfo,
        adHocSubProcess -> {
          adHocSubProcess.completionCondition("condition");
          adHocSubProcess.cancelRemainingInstances(true);
          adHocSubProcess.task("A");
          adHocSubProcess.task("B");
          adHocSubProcess.serviceTask("ServiceTask", b -> b.zeebeJobType("testType")).task("C");
        },
        Map.of("condition", true));

    client.newBroadcastSignalCommand().signalName("setup_signal").send().join();

    assertThat(recordsUpToSignal("setup_signal"))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .describedAs("Expect ad-hoc subprocess to be activated")
        .contains(tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED))
        .describedAs("Expect no activities to be activated")
        .doesNotContain(
            tuple("A", ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple("B", ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple("ServiceTask", ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));

    final var activatedAdHocSubprocess =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstance.getProcessInstanceKey())
            .withElementId(AD_HOC_SUB_PROCESS_ELEMENT_ID)
            .getFirst();

    // when
    client
        .newActivateAdHocSubprocessActivitiesCommand(
            String.valueOf(activatedAdHocSubprocess.getKey()))
        .activateElements("A", "ServiceTask")
        .send()
        .join();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstance.getProcessInstanceKey())
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .describedAs("Expect service task to be terminated after completion of A")
        .contains(
            tuple("A", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("ServiceTask", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("ServiceTask", ProcessInstanceIntent.TERMINATE_ELEMENT),
            tuple("ServiceTask", ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .describedAs(
            "Expect service task to never complete and task depending on service task to never be activated")
        .doesNotContain(
            tuple("ServiceTask", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("C", ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  void shouldReturnErrorOnCommandRejection(final TestInfo testInfo) {
    // given
    deployAndStartInstance(
        testInfo,
        adHocSubProcess -> {
          adHocSubProcess.task("A");
          adHocSubProcess.task("B");
          adHocSubProcess.task("C");
        });

    final var activatedAdHocSubprocess =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstance.getProcessInstanceKey())
            .withElementId(AD_HOC_SUB_PROCESS_ELEMENT_ID)
            .getFirst();

    // when/then
    assertThatThrownBy(
            () ->
                client
                    .newActivateAdHocSubprocessActivitiesCommand(
                        String.valueOf(activatedAdHocSubprocess.getKey()))
                    .activateElements("A", "A")
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 400: 'Bad Request'")
        .hasMessageContaining(
            "Command 'ACTIVATE' rejected with code 'INVALID_ARGUMENT': Expected to activate activities for ad-hoc subprocess with key '%s', but duplicate activities were given."
                .formatted(activatedAdHocSubprocess.getKey()));
  }

  private void deployAndStartInstance(
      final TestInfo testInfo, final Consumer<AdHocSubProcessBuilder> modifier) {
    deployAndStartInstance(testInfo, modifier, Collections.emptyMap());
  }

  private void deployAndStartInstance(
      final TestInfo testInfo,
      final Consumer<AdHocSubProcessBuilder> modifier,
      final Map<String, Object> variables) {
    processId = "process-" + testInfo.getTestMethod().get().getName();

    resourcesHelper.deployProcess(
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .adHocSubProcess(AD_HOC_SUB_PROCESS_ELEMENT_ID, modifier)
            .endEvent()
            .done());

    processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .variables(variables)
            .send()
            .join();
  }

  private ProcessInstanceRecordStream recordsUpToSignal(final String signalName) {
    return RecordingExporter.records()
        .limit(
            r ->
                r.getIntent() == SignalIntent.BROADCASTED
                    && ((SignalRecord) r.getValue()).getSignalName().equals(signalName))
        .processInstanceRecords()
        .withProcessInstanceKey(processInstance.getProcessInstanceKey());
  }
}

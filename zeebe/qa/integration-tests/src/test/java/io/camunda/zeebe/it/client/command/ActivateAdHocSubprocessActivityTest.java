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
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.function.Predicate;
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
  public void init(final TestInfo testInfo) {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
    resourcesHelper = new ZeebeResourcesHelper(client);

    deploy(testInfo);
    processInstance =
        client.newCreateInstanceCommand().bpmnProcessId(processId).latestVersion().send().join();
  }

  @Test
  void shouldActivateAdHocSubprocessActivities(final TestInfo testInfo) {
    // allows us to wait for the signal to be broadcasted
    client.newBroadcastSignalCommand().signalName("signal").send().join();

    assertThat(
            RecordingExporter.records()
                .limit(signalBroadcasted("signal"))
                .processInstanceRecords()
                .withProcessInstanceKey(processInstance.getProcessInstanceKey()))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .contains(tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED))
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
        .contains(
            tuple("A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("C", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  void shouldReturnErrorOnCommandRejection(final TestInfo testInfo) {
    final var activatedAdHocSubprocess =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstance.getProcessInstanceKey())
            .withElementId(AD_HOC_SUB_PROCESS_ELEMENT_ID)
            .getFirst();

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

  private void deploy(final TestInfo testInfo) {
    processId = "process-" + testInfo.getTestMethod().get().getName();

    resourcesHelper.deployProcess(
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .adHocSubProcess(
                AD_HOC_SUB_PROCESS_ELEMENT_ID,
                adHocSubProcess -> {
                  adHocSubProcess.task("A");
                  adHocSubProcess.task("B");
                  adHocSubProcess.task("C");
                })
            .endEvent()
            .done());
  }

  private static Predicate<Record<RecordValue>> signalBroadcasted(final String signalName) {
    return r ->
        r.getIntent() == SignalIntent.BROADCASTED
            && ((SignalRecord) r.getValue()).getSignalName().equals(signalName);
  }
}

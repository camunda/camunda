/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.processing;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.qa.util.actuator.ActorClockActuator;
import io.camunda.zeebe.qa.util.actuator.ActorClockActuator.AddTimeRequest;
import io.camunda.zeebe.qa.util.actuator.BanningActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
final class BannedInstanceIT {

  public static final String PROCESS_ID = "process";

  @TestZeebe
  private static final TestStandaloneBroker ZEEBE =
      new TestStandaloneBroker()
          .withRecordingExporter(true)
          .withUnifiedConfig(cfg -> cfg.getSystem().setClockControlled(true));

  private final BanningActuator actuator = BanningActuator.of(ZEEBE);
  private final ActorClockActuator clock = ActorClockActuator.of(ZEEBE);

  @AutoClose private CamundaClient client;

  @BeforeEach
  void beforeEach() {
    client = ZEEBE.newClientBuilder().build();
  }

  @Test
  public void shouldAllowCancelProcessInstanceWhenInstanceIsBanned() {
    // given
    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent("start")
                .serviceTask("task", t -> t.zeebeJobType("type"))
                .endEvent("end")
                .done(),
            "process1.bpmn")
        .send()
        .join();

    final long processInstanceKey =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .send()
            .join()
            .getProcessInstanceKey();

    RecordingExporter.processInstanceRecords()
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.SERVICE_TASK)
        .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .await();

    // when
    actuator.ban(processInstanceKey);
    client.newCancelInstanceCommand(processInstanceKey).send().join();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.CANCEL)
                .withRecordKey(processInstanceKey)
                .exists())
        .describedAs("Expected to find cancel command for process instance")
        .isTrue();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .describedAs("Expected to find terminated event for process instance")
        .isTrue();
  }

  @Test
  public void shouldNotTriggerTimerEventWhenInstanceIsBanned() {
    // given
    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent("start")
                .intermediateCatchEvent("timer", b -> b.timerWithDurationExpression("duration"))
                .endEvent("end")
                .done(),
            "process1.bpmn")
        .send()
        .join();

    final long processInstanceKey =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .variables(Map.of("duration", "PT10M"))
            .send()
            .join()
            .getProcessInstanceKey();

    RecordingExporter.timerRecords()
        .withIntents(TimerIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    actuator.ban(processInstanceKey);
    client.newCancelInstanceCommand(processInstanceKey).send().join();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    clock.addTime(new AddTimeRequest(Duration.ofMinutes(15).toMillis()));

    // then
    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .timerRecords()
                .withIntent(TimerIntent.TRIGGER)
                .exists())
        .describedAs(
            "Expected not to find trigger event for timer because the process instance is terminated")
        .isFalse();
  }

  @Test
  public void shouldAllowCancelBannedInstanceWithIncident() {
    // given
    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(
                    "A",
                    b ->
                        b.zeebeJobType("jobTypeA")
                            .zeebeInputExpression("assert(x, x != null)", "y"))
                .endEvent()
                .done(),
            "process1.bpmn")
        .send()
        .join();

    final long processInstanceKey =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .send()
            .join()
            .getProcessInstanceKey();

    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when
    actuator.ban(processInstanceKey);
    client.newCancelInstanceCommand(processInstanceKey).send().join();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.CANCEL)
                .withRecordKey(processInstanceKey)
                .exists())
        .describedAs("Expected to find cancel command for process instance")
        .isTrue();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .describedAs("Expected to find terminated event for process instance")
        .isTrue();

    assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.RESOLVED)
                .withRecordKey(incident.getKey())
                .exists())
        .describedAs("Expected to find resolved incident for process instance")
        .isTrue();
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.processing;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.qa.util.actuator.BanningActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@AutoCloseResources
@ZeebeIntegration
final class BannedInstanceIT {

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @TestZeebe
  private final TestStandaloneBroker zeebe = new TestStandaloneBroker().withRecordingExporter(true);

  private final BanningActuator actuator = BanningActuator.of(zeebe);

  @AutoCloseResource private ZeebeClient client;

  @BeforeEach
  void beforeEach() {
    client = zeebe.newClientBuilder().build();
  }

  @Test
  public void shouldAllowCancelProcessInstanceWhenInstanceIsBanned() {
    // given
    final var processId = helper.getBpmnProcessId();
    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(processId)
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
            .bpmnProcessId(processId)
            .latestVersion()
            .send()
            .join()
            .getProcessInstanceKey();

    // when
    actuator.ban(processInstanceKey);
    client.newCancelInstanceCommand(processInstanceKey).send().join();

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.CANCEL)
                .withProcessInstanceKey(processInstanceKey))
        .isNotNull();

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .withProcessInstanceKey(processInstanceKey))
        .isNotNull();

    Assertions.assertThat(
            RecordingExporter.timerRecords(TimerIntent.CANCELED)
                .withProcessInstanceKey(processInstanceKey))
        .isNotNull();
  }

  @Test
  public void shouldNotTriggerTimerEventWhenInstanceIsBanned() {
    // given
    final var processId = helper.getBpmnProcessId();
    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(processId)
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
            .bpmnProcessId(processId)
            .latestVersion()
            .variables(Map.of("duration", "PT2S"))
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

    final long secondProcessInstanceKey =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .variable("duration", "PT3S")
            .send()
            .join()
            .getProcessInstanceKey();

    RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
        .withProcessInstanceKey(secondProcessInstanceKey)
        .await();

    // then
    Assertions.assertThat(
            RecordingExporter.timerRecords()
                .limit(
                    t ->
                        t.getValue().getProcessInstanceKey() == secondProcessInstanceKey
                            && t.getIntent() == TimerIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .filter(t -> t.getIntent() == TimerIntent.TRIGGER)
                .onlyCommands())
        .isEmpty();
  }

  @Test
  public void shouldAllowCancelBannedInstanceWithIncident() {
    // given
    final var processId = helper.getBpmnProcessId();
    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(processId)
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
            .bpmnProcessId(processId)
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
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.CANCEL)
                .withProcessInstanceKey(processInstanceKey))
        .isNotNull();

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .withProcessInstanceKey(processInstanceKey))
        .isNotNull();

    Assertions.assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.RESOLVED)
                .withRecordKey(incident.getKey()))
        .isNotNull();
  }
}

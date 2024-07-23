/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.processing;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.qa.util.actuator.BanningActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;

@AutoCloseResources
@ZeebeIntegration
final class TimerTriggerBannedInstanceIT {

  @TestZeebe
  private final TestStandaloneBroker zeebe = new TestStandaloneBroker().withRecordingExporter(true);

  @AutoCloseResource private CamundaClient client;

  @BeforeEach
  void beforeEach() {
    client = zeebe.newClientBuilder().build();
  }

  /**
   * Given a process with a timer event that triggers some time in the future, when the instance is
   * banned before the timer is triggered, we should not produce many Timer TRIGGER commands for
   * that timer.
   *
   * <p>This was a problem previously because every time the DueDateChecker ran, it finds the timer
   * instance of the banned instance. Since, we remove the timer record from the command cache after
   * writing the record to the log, we write banned instance's timer record to the command log again
   * and again.
   */
  @RegressionTest("https://github.com/camunda/camunda/issues/14213")
  public void shouldTriggerTimerEventOnlyOnceWhenInstanceIsBanned() {
    // given
    final var processId = "process1";
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
    final BanningActuator actuator = BanningActuator.of(zeebe);
    actuator.ban(processInstanceKey);

    // create a timer instance with a longer due date, so that banned instance's timer is also
    // triggered
    final long firstTriggeredProcessInstanceKey =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .variable("duration", "PT3S")
            .send()
            .join()
            .getProcessInstanceKey();

    RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
        .withProcessInstanceKey(firstTriggeredProcessInstanceKey)
        .await();

    final long shorterDueDateProcessInstanceKey =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .variable("duration", "PT1S")
            .send()
            .join()
            .getProcessInstanceKey();

    RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
        .withProcessInstanceKey(shorterDueDateProcessInstanceKey)
        .await();

    // then
    Assertions.assertThat(
            RecordingExporter.timerRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(t -> t.getIntent() == TimerIntent.TRIGGERED)
                .filter(t -> t.getIntent() == TimerIntent.TRIGGER)
                .onlyCommands())
        .describedAs("We only expect a single TRIGGER command for the banned instance")
        .hasSize(1);
  }
}

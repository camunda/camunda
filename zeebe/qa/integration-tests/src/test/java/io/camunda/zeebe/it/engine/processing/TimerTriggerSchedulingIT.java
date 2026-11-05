/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.processing;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.qa.util.actuator.ActorClockActuator;
import io.camunda.zeebe.qa.util.actuator.ActorClockActuator.AddTimeRequest;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
final class TimerTriggerSchedulingIT {

  @TestZeebe
  private static final TestStandaloneBroker ZEEBE =
      new TestStandaloneBroker()
          .withRecordingExporter(true)
          .withUnifiedConfig(cfg -> cfg.getSystem().setClockControlled(true));

  private final ActorClockActuator clock = ActorClockActuator.of(ZEEBE);

  private CamundaClient client;
  private ZeebeResourcesHelper resourcesHelper;

  @BeforeEach
  void createClient() {
    client = ZEEBE.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(10)).build();
    resourcesHelper = new ZeebeResourcesHelper(client);
  }

  @AfterEach
  void closeClient() {
    client.close();
    clock.resetTime();
  }

  /**
   * Regression test against issue <a
   * href="https://github.com/camunda/camunda/issues/17128">17128</a>
   *
   * <p>Given a process with a timer event that triggers some time in the future, and a process that
   * schedules a timer event every second, we should not produce many Timer TRIGGER commands for any
   * of these timers.
   *
   * <p>This was a problem previously because every time the DueDateCheckScheduler ran, it would
   * reschedule another execution for the next timer (the one further in the future). This would
   * cause the DueDateCheckScheduler to run many times in a row for this same timer, and not benefit
   * from the command cache because executions are scheduled beforehand and executed before the
   * cache is persisted.
   */
  @Test
  void shouldTriggerTimerOnlyOnce() {
    // given
    final long processDefinitionKey =
        resourcesHelper.deployProcess(
            Bpmn.createExecutableProcess("PROCESS")
                .startEvent()
                .intermediateCatchEvent("timer", t -> t.timerWithDurationExpression("duration"))
                .endEvent()
                .done());
    final long processInstanceKey =
        resourcesHelper.createProcessInstance(
            processDefinitionKey,
            """
            {
              "duration": "PT1H"
            }
            """);

    // when
    for (int i = 0; i < 10; i++) {
      final long shortProcessInstanceKey =
          resourcesHelper.createProcessInstance(
              processDefinitionKey,
              """
              {
                "duration": "PT1S"
              }
              """);
      RecordingExporter.timerRecords(TimerIntent.CREATED)
          .withProcessInstanceKey(shortProcessInstanceKey)
          .await();
      clock.addTime(new AddTimeRequest(Duration.ofSeconds(2).toMillis()));
      RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
          .withProcessInstanceKey(shortProcessInstanceKey)
          .await();
    }

    clock.addTime(new AddTimeRequest(Duration.ofHours(2).toMillis()));

    // then
    Assertions.assertThat(
            RecordingExporter.timerRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(t -> t.getIntent() == TimerIntent.TRIGGERED)
                .filter(t -> t.getIntent() == TimerIntent.TRIGGER)
                .onlyCommands())
        .describedAs("We only expect a single TRIGGER command")
        .hasSize(1);
  }
}

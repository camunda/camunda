/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.processing;

import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class TimerTriggerSchedulingTest {

  private static final EmbeddedBrokerRule BROKER_RULE = new EmbeddedBrokerRule();
  private static final GrpcClientRule CLIENT_RULE = new GrpcClientRule(BROKER_RULE);

  @ClassRule
  public static final RuleChain RULE_CHAIN = RuleChain.outerRule(BROKER_RULE).around(CLIENT_RULE);

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  /**
   * Regression test against issue <a
   * href="https://github.com/camunda/camunda/issues/17128">17128</a>
   *
   * <p>Given a process with a timer event that triggers some time in the future, and a process that
   * schedules a timer event every second, we should not produce many Timer TRIGGER commands for any
   * of these timers.
   *
   * <p>This was a problem previously because every time the DueDateChecker ran, it would reschedule
   * another execution for the next timer (the one further in the future). This would cause the
   * DueDateChecker to run many times in a row for this same timer, and not benefit from the command
   * cache because executions are scheduled beforehand and executed before the cache is persisted.
   */
  @Test
  public void shouldTriggerTimerOnlyOnce() {
    // given
    final long processDefinitionKey =
        CLIENT_RULE.deployProcess(
            Bpmn.createExecutableProcess("PROCESS")
                .startEvent()
                .intermediateCatchEvent("timer", t -> t.timerWithDurationExpression("duration"))
                .endEvent()
                .done());
    final long processInstanceKey =
        CLIENT_RULE.createProcessInstance(
            processDefinitionKey,
            """
            {
              "duration": "PT1H"
            }
            """);

    // when
    for (int i = 0; i < 10; i++) {
      final long shortProcessInstanceKey =
          CLIENT_RULE.createProcessInstance(
              processDefinitionKey,
              """
              {
                "duration": "PT1S"
              }
              """);
      RecordingExporter.timerRecords(TimerIntent.CREATED)
          .withProcessInstanceKey(shortProcessInstanceKey)
          .await();
      BROKER_RULE.getClock().addTime(Duration.ofSeconds(2));
      RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
          .withProcessInstanceKey(shortProcessInstanceKey)
          .await();
    }

    BROKER_RULE.getClock().addTime(Duration.ofHours(2));

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

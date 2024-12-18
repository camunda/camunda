/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;

public class ReachEndOfLogTest {

  @Rule public final EngineRule engineRule = EngineRule.singlePartition();

  @Test
  public void shouldReturnTrueIfNothingProcessed() {
    // then -- eventually reaches the end. Needs to wait to account for internal processing.
    Awaitility.await("Processor should reach the end")
        .timeout(Duration.ofSeconds(5))
        .until(engineRule::hasReachedEnd);
  }

  @Test
  public void shouldReturnTrueAfterReachingEndOfTheLog() {
    // given
    engineRule
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess("process").startEvent().endEvent().done())
        .deploy();

    // when
    engineRule.processInstance().ofBpmnProcessId("process").create();

    // then
    Awaitility.await("Processor should reach the end")
        .atLeast(Duration.ofMillis(100))
        .until(engineRule::hasReachedEnd, Boolean.TRUE::equals);
  }

  @Test
  public void shouldReturnFalseIfNotReachedEndOfLog() {
    // given
    engineRule
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .exclusiveGateway("test")
                .defaultFlow()
                .connectTo("test")
                .sequenceFlowId("sf2")
                .condition("= false")
                .endEvent()
                .done())
        .deploy();

    // when
    engineRule.processInstance().ofBpmnProcessId("process").create();

    // then
    final var reachedEnd = engineRule.hasReachedEnd();
    assertThat(reachedEnd).isFalse();
  }
}

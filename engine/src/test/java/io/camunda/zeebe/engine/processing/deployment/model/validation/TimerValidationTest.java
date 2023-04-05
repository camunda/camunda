/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import static io.camunda.zeebe.engine.processing.deployment.model.validation.ExpectedValidationResult.expect;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractCatchEventBuilder;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import io.camunda.zeebe.model.bpmn.instance.TimerEventDefinition;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public final class TimerValidationTest {

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("timerEvents")
  @DisplayName("static timer expression with invalid cycle format")
  void invalidCycleFormat(
      final String timerEventElementId, final AbstractCatchEventBuilder<?, ?> timerEventBuilder) {

    final var process = timerEventBuilder.timerWithCycle("foo").done();

    ProcessValidationUtil.validateProcess(
        process, expect(timerEventElementId, "Invalid timer cycle expression"));
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("timerEvents")
  @DisplayName("static timer expression with invalid duration format")
  void invalidDurationFormat(
      final String timerEventElementId, final AbstractCatchEventBuilder<?, ?> timerEventBuilder) {

    final var process = timerEventBuilder.timerWithDuration("foo").done();

    ProcessValidationUtil.validateProcess(
        process,
        expect(
            timerEventElementId,
            "Invalid timer duration expression (Invalid duration format 'foo' for expression 'foo')"));
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("timerEvents")
  @DisplayName("static timer expression with invalid date format")
  void invalidDateFormat(
      final String timerEventElementId, final AbstractCatchEventBuilder<?, ?> timerEventBuilder) {

    final var process = timerEventBuilder.timerWithDate("foo").done();

    ProcessValidationUtil.validateProcess(
        process,
        expect(
            timerEventElementId,
            "Invalid timer date expression (Invalid date-time format 'foo' for expression 'foo')"));
  }

  @ParameterizedTest(name = "[{index}] {1}")
  @MethodSource("timerStartEventsWithExpression")
  @DisplayName("timer expression of start event with variable access")
  void invalidStartEventExpressionWithVariable(
      final String eventType,
      final String timerType,
      final Function<String, BpmnModelInstance> timerEventWithExpressionBuilder) {

    final var process = timerEventWithExpressionBuilder.apply("x");

    ProcessValidationUtil.validateProcess(
        process,
        expect(
            StartEvent.class,
            "Invalid timer "
                + timerType
                + " expression (failed to evaluate expression 'x': no variable found for name 'x')"));
  }

  @ParameterizedTest(name = "[{index}] {0} with {1}")
  @MethodSource("timerEventsWithExpression")
  @DisplayName("timer expression is not parsable")
  void notParsableTimerExpression(
      final String eventType,
      final String timerType,
      final Function<String, BpmnModelInstance> timerEventWithExpressionBuilder) {

    final var process = timerEventWithExpressionBuilder.apply("!");

    ProcessValidationUtil.validateProcess(
        process, expect(TimerEventDefinition.class, "failed to parse expression '!'"));
  }

  private static Stream<Arguments> timerEvents() {
    return Stream.of(
        Arguments.of(
            "start-event", Bpmn.createExecutableProcess("process").startEvent("start-event")),
        Arguments.of(
            "boundary-event",
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("test"))
                .boundaryEvent("boundary-event")),
        Arguments.of(
            "intermediate-catch-event",
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .intermediateCatchEvent("intermediate-catch-event")),
        Arguments.of(
            "event-sub-process",
            Bpmn.createExecutableProcess("process")
                .eventSubProcess("subprocess")
                .startEvent("event-sub-process")));
  }

  private static Stream<Arguments> timerStartEventsWithExpression() {
    return Stream.of(
        Arguments.of(
            "start event",
            "cycle",
            processBuilder(
                expression ->
                    Bpmn.createExecutableProcess("process")
                        .startEvent()
                        .timerWithCycleExpression(expression)
                        .done())),
        Arguments.of(
            "start event",
            "date",
            processBuilder(
                expression ->
                    Bpmn.createExecutableProcess("process")
                        .startEvent()
                        .timerWithDateExpression(expression)
                        .done())));
  }

  private static Stream<Arguments> timerEventsWithExpression() {
    final var otherTimerEventsWithExpressions =
        Stream.of(
            Arguments.of(
                "boundary event",
                "duration",
                processBuilder(
                    expression ->
                        Bpmn.createExecutableProcess("process")
                            .startEvent()
                            .serviceTask("task", t -> t.zeebeJobType("test"))
                            .boundaryEvent("boundary-event")
                            .timerWithDurationExpression(expression)
                            .done())),
            Arguments.of(
                "boundary event",
                "cycle",
                processBuilder(
                    expression ->
                        Bpmn.createExecutableProcess("process")
                            .startEvent()
                            .serviceTask("task", t -> t.zeebeJobType("test"))
                            .boundaryEvent("boundary-event")
                            .timerWithCycleExpression(expression)
                            .done())),
            Arguments.of(
                "intermediate catch event",
                "duration",
                processBuilder(
                    expression ->
                        Bpmn.createExecutableProcess("process")
                            .startEvent()
                            .intermediateCatchEvent("intermediate-catch-event")
                            .timerWithDurationExpression(expression)
                            .done())),
            Arguments.of(
                "event sub-process",
                "duration",
                processBuilder(
                    expression ->
                        Bpmn.createExecutableProcess("process")
                            .eventSubProcess(
                                "sub-process",
                                subProcess ->
                                    subProcess
                                        .startEvent()
                                        .timerWithDurationExpression(expression)
                                        .endEvent())
                            .startEvent()
                            .endEvent()
                            .done())),
            Arguments.of(
                "event sub-process",
                "cycle",
                processBuilder(
                    expression ->
                        Bpmn.createExecutableProcess("process")
                            .eventSubProcess(
                                "sub-process",
                                subProcess ->
                                    subProcess
                                        .startEvent()
                                        .timerWithCycleExpression(expression)
                                        .endEvent())
                            .startEvent()
                            .endEvent()
                            .done())),
            Arguments.of(
                "event sub-process",
                "date",
                processBuilder(
                    expression ->
                        Bpmn.createExecutableProcess("process")
                            .eventSubProcess(
                                "sub-process",
                                subProcess ->
                                    subProcess
                                        .startEvent()
                                        .timerWithDateExpression(expression)
                                        .endEvent())
                            .startEvent()
                            .endEvent()
                            .done())));
    return Stream.concat(timerStartEventsWithExpression(), otherTimerEventsWithExpressions);
  }

  private static Function<String, BpmnModelInstance> processBuilder(
      final Function<String, BpmnModelInstance> builder) {
    return builder;
  }
}

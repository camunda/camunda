/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.deployment.model.validation;

import static io.zeebe.engine.processing.deployment.model.validation.ExpectedValidationResult.expect;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.el.ExpressionLanguage;
import io.zeebe.el.ExpressionLanguageFactory;
import io.zeebe.engine.processing.common.ExpressionProcessor;
import io.zeebe.engine.processing.common.ExpressionProcessor.VariablesLookup;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.AbstractCatchEventBuilder;
import io.zeebe.model.bpmn.instance.StartEvent;
import io.zeebe.model.bpmn.instance.TimerEventDefinition;
import io.zeebe.model.bpmn.traversal.ModelWalker;
import io.zeebe.model.bpmn.validation.ValidationVisitor;
import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.camunda.bpm.model.xml.validation.ValidationResults;
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

    validateProcess(
        process,
        expect(
            timerEventElementId,
            "Invalid timer cycle expression (Repetition spec must start with R)"));
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("timerEvents")
  @DisplayName("static timer expression with invalid duration format")
  void invalidDurationFormat(
      final String timerEventElementId, final AbstractCatchEventBuilder<?, ?> timerEventBuilder) {

    final var process = timerEventBuilder.timerWithDuration("foo").done();

    validateProcess(
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

    validateProcess(
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

    validateProcess(
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

    validateProcess(process, expect(TimerEventDefinition.class, "failed to parse expression '!'"));
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
                            .done())));
    return Stream.concat(timerStartEventsWithExpression(), otherTimerEventsWithExpressions);
  }

  private static Function<String, BpmnModelInstance> processBuilder(
      final Function<String, BpmnModelInstance> builder) {
    return builder;
  }

  private void validateProcess(
      final BpmnModelInstance process, final ExpectedValidationResult expectation) {

    Bpmn.validateModel(process);

    final var validationResults =
        validate(process).getResults().values().stream()
            .flatMap(Collection::stream)
            .collect(Collectors.toList());

    final var validationResultsAsString =
        validationResults.stream()
            .map(ExpectedValidationResult::toString)
            .collect(Collectors.joining(",\n"));

    assertThat(validationResults)
        .describedAs(
            "Expected validation failure%n<%s>%n but actual validation validationResults was%n<%s>",
            expectation, validationResultsAsString)
        .anyMatch(expectation::matches);
  }

  private static ValidationResults validate(final BpmnModelInstance model) {
    final ModelWalker walker = new ModelWalker(model);
    final ExpressionLanguage expressionLanguage =
        ExpressionLanguageFactory.createExpressionLanguage();
    final VariablesLookup emptyLookup = (name, scopeKey) -> null;
    final var expressionProcessor = new ExpressionProcessor(expressionLanguage, emptyLookup);
    final ValidationVisitor visitor =
        new ValidationVisitor(
            ZeebeRuntimeValidators.getValidators(expressionLanguage, expressionProcessor));
    walker.walk(visitor);

    return visitor.getValidationResult();
  }
}

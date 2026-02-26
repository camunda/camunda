/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.engine.processing.bpmn.clock.ZeebeFeelEngineClock;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEvent;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableConditional;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.BpmnTransformer;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ConditionalEventDefinitionBuilder;
import java.time.InstantSource;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class ConditionalEventTransformerTest {

  private static final String CONDITIONAL_ID = "conditional";

  private final ExpressionLanguage expressionLanguage =
      ExpressionLanguageFactory.createExpressionLanguage(
          new ZeebeFeelEngineClock(InstantSource.system()));
  private final BpmnTransformer transformer = new BpmnTransformer(expressionLanguage);

  private BpmnModelInstance processWithConditionalBoundaryEvent(
      final Consumer<ConditionalEventDefinitionBuilder> modifier) {
    return Bpmn.createExecutableProcess()
        .startEvent()
        .serviceTask("task")
        .zeebeJobType("task")
        .boundaryEvent(CONDITIONAL_ID)
        .condition(modifier)
        .endEvent()
        .moveToActivity("task")
        .endEvent()
        .done();
  }

  private BpmnModelInstance processWithConditionalIntermediateCatchEvent(
      final Consumer<ConditionalEventDefinitionBuilder> modifier) {
    return Bpmn.createExecutableProcess()
        .startEvent()
        .intermediateCatchEvent(CONDITIONAL_ID)
        .condition(modifier)
        .endEvent()
        .done();
  }

  private BpmnModelInstance processWithEventSubprocessConditionalStartEvent(
      final Consumer<ConditionalEventDefinitionBuilder> modifier) {
    return Bpmn.createExecutableProcess("process")
        .startEvent()
        .endEvent()
        .moveToProcess("process")
        .eventSubProcess()
        .startEvent(CONDITIONAL_ID)
        .condition(modifier)
        .endEvent()
        .subProcessDone()
        .done();
  }

  private BpmnModelInstance processWithConditionalStartEvent(
      final Consumer<ConditionalEventDefinitionBuilder> modifier) {
    return Bpmn.createExecutableProcess().startEvent(CONDITIONAL_ID).condition(modifier).done();
  }

  private ExecutableConditional transformConditionalEvent(final BpmnModelInstance conditional) {
    final List<ExecutableProcess> processes = transformer.transformDefinitions(conditional);
    return processes
        .get(0)
        .getElementById(CONDITIONAL_ID, ExecutableCatchEvent.class)
        .getConditional();
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class ConditionTests {

    Stream<Arguments> conditions() {
      return Stream.of(
          Arguments.of("x > 1", "x > 1"),
          Arguments.of("=x > 1", "x > 1"),
          Arguments.of("=true", "true"),
          Arguments.of("=false", "false"));
    }

    @DisplayName("Should transform conditional boundary events with condition")
    @ParameterizedTest
    @MethodSource("conditions")
    void shouldTransformConditionalBoundaryEvent(
        final String condition, final String parsedExpression) {
      final var executableConditional =
          transformConditionalEvent(
              processWithConditionalBoundaryEvent(c -> c.condition(condition)));
      if (parsedExpression == null) {
        assertThat(executableConditional.getConditionExpression()).isNull();
      } else {
        assertThat(executableConditional.getConditionExpression().getExpression())
            .isEqualTo(parsedExpression);
      }
    }

    @DisplayName("Should transform intermediate conditional catch events with condition")
    @ParameterizedTest
    @MethodSource("conditions")
    void shouldTransformIntermediateConditionalEvent(
        final String condition, final String parsedExpression) {
      final var executableConditional =
          transformConditionalEvent(
              processWithConditionalIntermediateCatchEvent(c -> c.condition(condition)));
      if (parsedExpression == null) {
        assertThat(executableConditional.getConditionExpression()).isNull();
      } else {
        assertThat(executableConditional.getConditionExpression().getExpression())
            .isEqualTo(parsedExpression);
      }
    }

    @DisplayName("Should transform event subprocess conditional start events with condition")
    @ParameterizedTest
    @MethodSource("conditions")
    void shouldTransformEventSubprocessConditionalStartEvent(
        final String condition, final String parsedExpression) {
      final var executableConditional =
          transformConditionalEvent(
              processWithEventSubprocessConditionalStartEvent(c -> c.condition(condition)));
      if (parsedExpression == null) {
        assertThat(executableConditional.getConditionExpression()).isNull();
      } else {
        assertThat(executableConditional.getConditionExpression().getExpression())
            .isEqualTo(parsedExpression);
      }
    }

    @DisplayName("Should transform conditional start events with condition")
    @ParameterizedTest
    @MethodSource("conditions")
    void shouldTransformConditionalStartEvent(
        final String condition, final String parsedExpression) {
      final var executableConditional =
          transformConditionalEvent(processWithConditionalStartEvent(c -> c.condition(condition)));
      if (parsedExpression == null) {
        assertThat(executableConditional.getConditionExpression()).isNull();
      } else {
        assertThat(executableConditional.getConditionExpression().getExpression())
            .isEqualTo(parsedExpression);
      }
    }

    @Test
    void shouldThrowExceptionForInvalidConditionExpression() {
      final var exception =
          org.junit.jupiter.api.Assertions.assertThrows(
              IllegalStateException.class,
              () ->
                  transformConditionalEvent(
                      processWithConditionalBoundaryEvent(c -> c.condition(""))));
      assertThat(exception.getMessage())
          .contains("The condition expression must be non-static. Found static expression ''.");
    }
  }

  @Nested
  class ConditionalFilterTests {

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class VariableNamesTests {

      Stream<Arguments> expressionsWithVariableNames() {
        return Stream.of(
            Arguments.of("=x > 10", List.of("x")),
            Arguments.of("=x > 10 and y < 5", List.of("x", "y")),
            Arguments.of("=x.y > 10", List.of("x")),
            Arguments.of("=x + y > 10", List.of("x", "y")),
            Arguments.of("=x > 10 or y < 5 or z = 0", List.of("x", "y", "z")));
      }

      @DisplayName("Should auto-detect variableNames from FEEL expression for boundary event")
      @ParameterizedTest
      @MethodSource("expressionsWithVariableNames")
      void shouldAutoDetectVariableNamesForConditionalBoundaryEvent(
          final String expression, final List<String> expectedVariableNames) {
        final var executableConditional =
            transformConditionalEvent(
                processWithConditionalBoundaryEvent(c -> c.condition(expression)));
        assertThat(executableConditional.getVariableNames()).isEqualTo(expectedVariableNames);
      }

      @DisplayName(
          "Should auto-detect variableNames from FEEL expression for intermediate catch event")
      @ParameterizedTest
      @MethodSource("expressionsWithVariableNames")
      void shouldAutoDetectVariableNamesForIntermediateConditionalEvent(
          final String expression, final List<String> expectedVariableNames) {
        final var executableConditional =
            transformConditionalEvent(
                processWithConditionalIntermediateCatchEvent(c -> c.condition(expression)));
        assertThat(executableConditional.getVariableNames()).isEqualTo(expectedVariableNames);
      }

      @DisplayName(
          "Should auto-detect variableNames from FEEL expression for event subprocess start event")
      @ParameterizedTest
      @MethodSource("expressionsWithVariableNames")
      void shouldAutoDetectVariableNamesForSubprocessConditionalStartEvent(
          final String expression, final List<String> expectedVariableNames) {
        final var executableConditional =
            transformConditionalEvent(
                processWithEventSubprocessConditionalStartEvent(c -> c.condition(expression)));
        assertThat(executableConditional.getVariableNames()).isEqualTo(expectedVariableNames);
      }

      @DisplayName("Should auto-detect variableNames from FEEL expression for start event")
      @ParameterizedTest
      @MethodSource("expressionsWithVariableNames")
      void shouldAutoDetectVariableNamesForConditionalStartEvent(
          final String expression, final List<String> expectedVariableNames) {
        final var executableConditional =
            transformConditionalEvent(
                processWithConditionalStartEvent(c -> c.condition(expression)));
        assertThat(executableConditional.getVariableNames()).isEqualTo(expectedVariableNames);
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class VariableEventsTests {
      Stream<Arguments> variableEvents() {
        return Stream.of(
            Arguments.of(null, List.of()),
            Arguments.of("", List.of()),
            Arguments.of(" ", List.of()),
            Arguments.of("create", List.of("create")),
            Arguments.of("update", List.of("update")),
            Arguments.of("create, update", List.of("create", "update")),
            Arguments.of(" create , update ", List.of("create", "update")));
      }

      @DisplayName("Should transform conditional boundary events with variableEvents")
      @ParameterizedTest
      @MethodSource("variableEvents")
      void shouldTransformConditionalBoundaryEvent(
          final String variableEvents, final List<String> parsedVariableEvents) {
        final var executableConditional =
            transformConditionalEvent(
                processWithConditionalBoundaryEvent(c -> c.zeebeVariableEvents(variableEvents)));
        assertThat(executableConditional.getVariableEvents()).isEqualTo(parsedVariableEvents);
      }
    }
  }
}

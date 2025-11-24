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
import io.camunda.zeebe.engine.GlobalListenersConfiguration;
import io.camunda.zeebe.engine.processing.bpmn.clock.ZeebeFeelEngineClock;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEvent;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableConditional;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.BpmnTransformer;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ConditionalEventDefinitionBuilder;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class ConditionalEventTransformerTest {

  private static final String CONDITIONAL_ID = "conditional";

  private final ExpressionLanguage expressionLanguage =
      ExpressionLanguageFactory.createExpressionLanguage(
          new ZeebeFeelEngineClock(InstantSource.system()));
  private final GlobalListenersConfiguration globalListenersConfiguration =
      new GlobalListenersConfiguration(new ArrayList<>());
  private final BpmnTransformer transformer =
      new BpmnTransformer(expressionLanguage, globalListenersConfiguration);

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
          Arguments.of(null, null),
          Arguments.of("", null),
          Arguments.of(" ", null),
          Arguments.of("x > 1", "x > 1"),
          Arguments.of("=x > 1", "x > 1"));
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
  }

  @Nested
  class ConditionalFilterTests {

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class VariableNamesTests {
      Stream<Arguments> variableNamesExpressions() {
        return Stream.of(
            Arguments.of(null, null),
            Arguments.of("", null),
            Arguments.of(" ", null),
            Arguments.of("var1", "var1"),
            Arguments.of("var1,var2", "var1,var2"),
            Arguments.of(" var1 , var2 ", " var1 , var2 "),
            Arguments.of("=obj1.var1", "obj1.var1"),
            Arguments.of("=[\"var1\",\"var2\"]", "[\"var1\",\"var2\"]"));
      }

      Stream<Arguments> variableNames() {
        return Stream.of(
            Arguments.of(null, null),
            Arguments.of("", null),
            Arguments.of(" ", null),
            Arguments.of("var1", List.of("var1")),
            Arguments.of("var1,var2", List.of("var1", "var2")),
            Arguments.of(" var1 , var2 ", List.of("var1", "var2")),
            Arguments.of("=obj1.var1", null),
            Arguments.of(
                "=[\"var1\",\"var2\"]",
                null)); // expressions will only be evaluated at runtime for non-static variable
        // names
      }

      @DisplayName("Should transform conditional boundary events with variableNames expressions")
      @ParameterizedTest
      @MethodSource("variableNamesExpressions")
      void shouldTransformConditionalBoundaryEvent(
          final String variableNames, final String parsedExpression) {
        final var executableConditional =
            transformConditionalEvent(
                processWithConditionalBoundaryEvent(c -> c.zeebeVariableNames(variableNames)));
        if (parsedExpression == null) {
          assertThat(executableConditional.getVariableNamesExpression()).isNull();
        } else {
          assertThat(executableConditional.getVariableNamesExpression().getExpression())
              .isEqualTo(parsedExpression);
        }
      }

      @DisplayName("Should transform conditional boundary events with variableNames")
      @ParameterizedTest
      @MethodSource("variableNames")
      void shouldTransformStaticVariableNamesForConditionalBoundaryEvent(
          final String variableNames, final List<String> parsedVariableNames) {
        final var executableConditional =
            transformConditionalEvent(
                processWithConditionalBoundaryEvent(c -> c.zeebeVariableNames(variableNames)));
        assertThat(executableConditional.getVariableNames()).isEqualTo(parsedVariableNames);
      }

      @DisplayName(
          "Should transform intermediate conditional catch events with variableNames expressions")
      @ParameterizedTest
      @MethodSource("variableNamesExpressions")
      void shouldTransformIntermediateConditionalEvent(
          final String variableNames, final String parsedExpression) {
        final var executableConditional =
            transformConditionalEvent(
                processWithConditionalIntermediateCatchEvent(
                    c -> c.zeebeVariableNames(variableNames)));
        if (parsedExpression == null) {
          assertThat(executableConditional.getVariableNamesExpression()).isNull();
        } else {
          assertThat(executableConditional.getVariableNamesExpression().getExpression())
              .isEqualTo(parsedExpression);
        }
      }

      @DisplayName("Should transform intermediate conditional catch events with variableNames")
      @ParameterizedTest
      @MethodSource("variableNames")
      void shouldTransformStaticVariableNamesForIntermediateConditionalEvent(
          final String variableNames, final List<String> parsedVariableNames) {
        final var executableConditional =
            transformConditionalEvent(
                processWithConditionalIntermediateCatchEvent(
                    c -> c.zeebeVariableNames(variableNames)));
        assertThat(executableConditional.getVariableNames()).isEqualTo(parsedVariableNames);
      }

      @DisplayName(
          "Should transform event subprocess conditional start events with variableNames expressions")
      @ParameterizedTest
      @MethodSource("variableNamesExpressions")
      void shouldTransformEventSubprocessConditionalStartEvent(
          final String variableNames, final String parsedExpression) {
        final var executableConditional =
            transformConditionalEvent(
                processWithEventSubprocessConditionalStartEvent(
                    c -> c.zeebeVariableNames(variableNames)));
        if (parsedExpression == null) {
          assertThat(executableConditional.getVariableNamesExpression()).isNull();
        } else {
          assertThat(executableConditional.getVariableNamesExpression().getExpression())
              .isEqualTo(parsedExpression);
        }
      }

      @DisplayName("Should transform event subprocess conditional start events with variableNames")
      @ParameterizedTest
      @MethodSource("variableNames")
      void shouldTransformStaticVariableNamesForSubprocessConditionalStartEvent(
          final String variableNames, final List<String> parsedVariableNames) {
        final var executableConditional =
            transformConditionalEvent(
                processWithEventSubprocessConditionalStartEvent(
                    c -> c.zeebeVariableNames(variableNames)));
        assertThat(executableConditional.getVariableNames()).isEqualTo(parsedVariableNames);
      }

      // only static expressions or expressions based on literals are valid
      Stream<Arguments> variableNamesExpressionsForStartEvent() {
        return Stream.of(
            Arguments.of(null, null),
            Arguments.of("", null),
            Arguments.of(" ", null),
            Arguments.of("var1", "var1"),
            Arguments.of("var1,var2", "var1,var2"),
            Arguments.of(" var1 , var2 ", " var1 , var2 "),
            Arguments.of("=[\"var1\",\"var2\"]", "[\"var1\",\"var2\"]"));
      }

      // only static expressions or expressions based on literals are valid
      Stream<Arguments> variableNamesForStartEvent() {
        return Stream.of(
            Arguments.of(null, null),
            Arguments.of("", null),
            Arguments.of(" ", null),
            Arguments.of("var1", List.of("var1")),
            Arguments.of("var1,var2", List.of("var1", "var2")),
            Arguments.of(" var1 , var2 ", List.of("var1", "var2")),
            Arguments.of(
                "=[\"var1\",\"var2\"]",
                List.of(
                    "var1",
                    "var2"))); // non-static variable names is also evaluated to support expression
        // literals for root level start events
      }

      @DisplayName("Should transform conditional start events with variableNames expressions")
      @ParameterizedTest
      @MethodSource("variableNamesExpressionsForStartEvent")
      void shouldTransformConditionalStartEvent(
          final String variableNames, final String parsedExpression) {
        final var executableConditional =
            transformConditionalEvent(
                processWithConditionalStartEvent(c -> c.zeebeVariableNames(variableNames)));
        if (parsedExpression == null) {
          assertThat(executableConditional.getVariableNamesExpression()).isNull();
        } else {
          assertThat(executableConditional.getVariableNamesExpression().getExpression())
              .isEqualTo(parsedExpression);
        }
      }

      @DisplayName("Should transform conditional start events with variableNames")
      @ParameterizedTest
      @MethodSource("variableNamesForStartEvent")
      void shouldTransformStaticVariableNamesForConditionalStartEvent(
          final String variableNames, final List<String> parsedVariableNames) {
        final var executableConditional =
            transformConditionalEvent(
                processWithConditionalStartEvent(c -> c.zeebeVariableNames(variableNames)));
        assertThat(executableConditional.getVariableNames()).isEqualTo(parsedVariableNames);
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class VariableEventsTests {
      Stream<Arguments> variableEvents() {
        return Stream.of(
            Arguments.of(null, null),
            Arguments.of("", null),
            Arguments.of(" ", null),
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

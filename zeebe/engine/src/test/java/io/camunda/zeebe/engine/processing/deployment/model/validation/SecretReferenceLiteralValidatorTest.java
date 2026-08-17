/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.engine.processing.bpmn.clock.ZeebeFeelEngineClock;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeInput;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeProperty;
import java.time.InstantSource;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class SecretReferenceLiteralValidatorTest {

  /**
   * The two authoring locations behave identically, so every case runs against both the input
   * mapping source and the property value.
   */
  private static Stream<Arguments> validators() {
    return Stream.of(
        Arguments.of("input", inputValidator()), Arguments.of("property", propertyValidator()));
  }

  @ParameterizedTest(name = "[{0}] rejects static value that is a secret reference")
  @MethodSource("validators")
  void shouldRejectStaticValueThatIsASecretReference(
      final String name, final BiConsumer<String, ValidationResultCollector> validate) {
    // when
    final var collector = validate("camunda.secrets.token", validate);

    // then
    verify(collector).addError(eq(0), contains("camunda.secrets.token"));
  }

  @ParameterizedTest(name = "[{0}] rejects FEEL string literal that is a secret reference")
  @MethodSource("validators")
  void shouldRejectFeelStringLiteralThatIsASecretReference(
      final String name, final BiConsumer<String, ValidationResultCollector> validate) {
    // when
    final var collector = validate("=\"camunda.secrets.token\"", validate);

    // then
    verify(collector).addError(eq(0), contains("camunda.secrets.token"));
  }

  @ParameterizedTest(name = "[{0}] rejects secret reference embedded in a string literal")
  @MethodSource("validators")
  void shouldRejectSecretReferenceEmbeddedInAStringLiteral(
      final String name, final BiConsumer<String, ValidationResultCollector> validate) {
    // when
    final var collector = validate("=\"Bearer camunda.secrets.token\"", validate);

    // then
    verify(collector).addError(eq(0), contains("camunda.secrets.token"));
  }

  @ParameterizedTest(name = "[{0}] allows secret reference used as an expression")
  @MethodSource("validators")
  void shouldAllowSecretReferenceUsedAsAnExpression(
      final String name, final BiConsumer<String, ValidationResultCollector> validate) {
    // when
    final var collector = validate("=camunda.secrets.token", validate);

    // then
    verifyNoInteractions(collector);
  }

  @ParameterizedTest(name = "[{0}] allows secret reference expression inside concatenation")
  @MethodSource("validators")
  void shouldAllowSecretReferenceExpressionInsideConcatenation(
      final String name, final BiConsumer<String, ValidationResultCollector> validate) {
    // when
    final var collector = validate("=\"Bearer \" + camunda.secrets.token", validate);

    // then
    verifyNoInteractions(collector);
  }

  @ParameterizedTest(name = "[{0}] rejects secret reference inside object literal")
  @MethodSource("validators")
  void shouldRejectSecretReferenceInsideObjectLiteral(
      final String name, final BiConsumer<String, ValidationResultCollector> validate) {
    // when - the reference is a string literal nested in a constant object
    final var collector = validate("={\"auth\": \"camunda.secrets.token\"}", validate);

    // then
    verify(collector).addError(eq(0), contains("camunda.secrets.token"));
  }

  @ParameterizedTest(name = "[{0}] rejects secret reference inside list literal")
  @MethodSource("validators")
  void shouldRejectSecretReferenceInsideListLiteral(
      final String name, final BiConsumer<String, ValidationResultCollector> validate) {
    // when - the reference is a string literal nested in a constant list
    final var collector = validate("=[\"camunda.secrets.token\"]", validate);

    // then
    verify(collector).addError(eq(0), contains("camunda.secrets.token"));
  }

  @ParameterizedTest(name = "[{0}] allows secret reference expression inside object literal")
  @MethodSource("validators")
  void shouldAllowSecretReferenceExpressionInsideObjectLiteral(
      final String name, final BiConsumer<String, ValidationResultCollector> validate) {
    // when - the reference is an expression path inside the object, not a literal
    final var collector = validate("={\"auth\": camunda.secrets.token}", validate);

    // then
    verifyNoInteractions(collector);
  }

  @ParameterizedTest(name = "[{0}] allows cluster variable expression")
  @MethodSource("validators")
  void shouldAllowClusterVariableExpression(
      final String name, final BiConsumer<String, ValidationResultCollector> validate) {
    // when
    final var collector = validate("=camunda.vars.env.REGION", validate);

    // then
    verifyNoInteractions(collector);
  }

  @ParameterizedTest(name = "[{0}] allows plain static value")
  @MethodSource("validators")
  void shouldAllowPlainStaticValue(
      final String name, final BiConsumer<String, ValidationResultCollector> validate) {
    // when
    final var collector = validate("hello world", validate);

    // then
    verifyNoInteractions(collector);
  }

  @ParameterizedTest(name = "[{0}] allows plain string literal")
  @MethodSource("validators")
  void shouldAllowPlainStringLiteral(
      final String name, final BiConsumer<String, ValidationResultCollector> validate) {
    // when
    final var collector = validate("=\"hello world\"", validate);

    // then
    verifyNoInteractions(collector);
  }

  @ParameterizedTest(name = "[{0}] ignores null source")
  @MethodSource("validators")
  void shouldIgnoreNullSource(
      final String name, final BiConsumer<String, ValidationResultCollector> validate) {
    // when
    final var collector = validate(null, validate);

    // then
    verifyNoInteractions(collector);
  }

  @ParameterizedTest(name = "[{0}] reports every secret reference in an object literal")
  @MethodSource("validators")
  void shouldReportEverySecretReferenceInAnObjectLiteral(
      final String name, final BiConsumer<String, ValidationResultCollector> validate) {
    // when - two distinct references appear as literals in one object
    final var collector =
        validate(
            "={\"a\": \"camunda.secrets.tokenA\", \"b\": \"camunda.secrets.tokenB\"}", validate);

    // then - both are listed, not just the first
    verify(collector)
        .addError(
            eq(0),
            argThat(
                message ->
                    message.contains("camunda.secrets.tokenA")
                        && message.contains("camunda.secrets.tokenB")));
  }

  @ParameterizedTest(name = "[{0}] rejects secret reference containing digits")
  @MethodSource("validators")
  void shouldRejectSecretReferenceContainingDigits(
      final String name, final BiConsumer<String, ValidationResultCollector> validate) {
    // when
    final var collector = validate("camunda.secrets.token_2", validate);

    // then
    verify(collector).addError(eq(0), contains("camunda.secrets.token_2"));
  }

  private ValidationResultCollector validate(
      final String source, final BiConsumer<String, ValidationResultCollector> validate) {
    final var collector = mock(ValidationResultCollector.class);
    validate.accept(source, collector);
    return collector;
  }

  private static BiConsumer<String, ValidationResultCollector> inputValidator() {
    final var expressionLanguage =
        ExpressionLanguageFactory.createExpressionLanguage(
            new ZeebeFeelEngineClock(InstantSource.system()));
    final var sut = SecretReferenceLiteralValidator.forInput(expressionLanguage);
    return (source, collector) -> {
      final var element = mock(ZeebeInput.class);
      when(element.getSource()).thenReturn(source);
      sut.validate(element, collector);
    };
  }

  private static BiConsumer<String, ValidationResultCollector> propertyValidator() {
    final var expressionLanguage =
        ExpressionLanguageFactory.createExpressionLanguage(
            new ZeebeFeelEngineClock(InstantSource.system()));
    final var sut = SecretReferenceLiteralValidator.forProperty(expressionLanguage);
    return (source, collector) -> {
      final var element = mock(ZeebeProperty.class);
      when(element.getValue()).thenReturn(source);
      sut.validate(element, collector);
    };
  }
}

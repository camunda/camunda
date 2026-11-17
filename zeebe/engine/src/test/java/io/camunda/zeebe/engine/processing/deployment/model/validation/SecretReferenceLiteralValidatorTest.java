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

  /**
   * Regression for #59121: a multi-kilobyte FEEL string with many {@code \"} escapes (as in
   * connector payloads that embed JSON) must not StackOverflow while scanning for secret-reference
   * literals. A greedy string-literal regex recursed on the group loop and crashed the broker.
   */
  @ParameterizedTest(name = "[{0}] tolerates a long escaped FEEL string literal")
  @MethodSource("validators")
  void shouldTolerateLongEscapedFeelStringLiteral(
      final String name, final BiConsumer<String, ValidationResultCollector> validate) {
    // given - nested JSON-as-string with thousands of escaped quotes, no secret reference
    final var escapedJson =
        "{\\\"type\\\":\\\"AdaptiveCard\\\",\\\"body\\\":" + "\\\"x\\\"".repeat(2000) + "}";
    final var source = "={\"content\": \"" + escapedJson + "\"}";

    // when / then - must complete without StackOverflowError and accept the mapping
    final var collector = validate(source, validate);
    verifyNoInteractions(collector);
  }

  /**
   * Escape sequences must not be collapsed (e.g. {@code \t} → {@code t}), or a string such as
   * {@code camunda.secrets.\token} would be misread as the reference {@code camunda.secrets.token}.
   */
  @ParameterizedTest(name = "[{0}] does not treat FEEL escape as part of a secret reference name")
  @MethodSource("validators")
  void shouldNotRejectWhenEscapeBreaksSecretReferenceName(
      final String name, final BiConsumer<String, ValidationResultCollector> validate) {
    // when - \t is a FEEL tab escape, not the letter t in "token"
    final var collector = validate("=\"camunda.secrets.\\token\"", validate);

    // then
    verifyNoInteractions(collector);
  }

  /**
   * Companion to {@link #shouldTolerateLongEscapedFeelStringLiteral}: the same long escaped payload
   * must still reject a secret reference embedded as a quoted literal.
   */
  @ParameterizedTest(name = "[{0}] rejects secret reference inside a long escaped FEEL string")
  @MethodSource("validators")
  void shouldRejectSecretReferenceInsideLongEscapedFeelStringLiteral(
      final String name, final BiConsumer<String, ValidationResultCollector> validate) {
    // given
    final var escapedJson =
        "{\\\"auth\\\":\\\"camunda.secrets.token\\\",\\\"pad\\\":" + "\\\"x\\\"".repeat(2000) + "}";
    final var source = "={\"content\": \"" + escapedJson + "\"}";

    // when
    final var collector = validate(source, validate);

    // then
    verify(collector).addError(eq(0), contains("camunda.secrets.token"));
  }

  @ParameterizedTest(
      name = "[{0}] rejects static value that is a hyphenated secret reference, naming it in full")
  @MethodSource("validators")
  void shouldRejectStaticValueThatIsAHyphenatedSecretReference(
      final String name, final BiConsumer<String, ValidationResultCollector> validate) {
    // when - a static value, so what is rejected is the literal use, not the hyphen: a hyphenated
    // name is perfectly valid when written as an expression (see the test below)
    final var collector = validate("camunda.secrets.db-password", validate);

    // then - the whole name is named back to the author, not the 'camunda.secrets.db' prefix the
    // narrower charset used to report
    verify(collector).addError(eq(0), contains("camunda.secrets.db-password"));
  }

  @ParameterizedTest(name = "[{0}] allows backtick-escaped hyphenated reference as an expression")
  @MethodSource("validators")
  void shouldAllowBacktickedHyphenatedSecretReferenceUsedAsAnExpression(
      final String name, final BiConsumer<String, ValidationResultCollector> validate) {
    // when - the only way to write a dashed name in FEEL, since a bare dash is the minus operator
    final var collector = validate("=camunda.secrets.`db-password`", validate);

    // then - backticks are not string quotes, so the reference is an expression and passes
    verifyNoInteractions(collector);
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

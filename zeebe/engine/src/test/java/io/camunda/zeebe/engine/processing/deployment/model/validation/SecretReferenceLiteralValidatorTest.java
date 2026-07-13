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

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.engine.processing.bpmn.clock.ZeebeFeelEngineClock;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeInput;
import java.time.InstantSource;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;
import org.junit.jupiter.api.Test;

final class SecretReferenceLiteralValidatorTest {

  private final ExpressionLanguage expressionLanguage =
      ExpressionLanguageFactory.createExpressionLanguage(
          new ZeebeFeelEngineClock(InstantSource.system()));
  private final SecretReferenceLiteralValidator sut =
      new SecretReferenceLiteralValidator(expressionLanguage);

  @Test
  void shouldRejectStaticValueThatIsASecretReference() {
    // when
    final var collector = validate("camunda.secrets.token");

    // then
    verify(collector).addError(eq(0), contains("camunda.secrets.token"));
  }

  @Test
  void shouldRejectFeelStringLiteralThatIsASecretReference() {
    // when
    final var collector = validate("=\"camunda.secrets.token\"");

    // then
    verify(collector).addError(eq(0), contains("camunda.secrets.token"));
  }

  @Test
  void shouldRejectConstantFoldedStringLiteralThatIsASecretReference() {
    // when
    final var collector = validate("=\"camunda\" + \".secrets.token\"");

    // then
    verify(collector).addError(eq(0), contains("camunda.secrets.token"));
  }

  @Test
  void shouldRejectSecretReferenceEmbeddedInAStringLiteral() {
    // when
    final var collector = validate("=\"Bearer camunda.secrets.token\"");

    // then
    verify(collector).addError(eq(0), contains("camunda.secrets.token"));
  }

  @Test
  void shouldAllowSecretReferenceUsedAsAnExpression() {
    // when
    final var collector = validate("=camunda.secrets.token");

    // then
    verifyNoInteractions(collector);
  }

  @Test
  void shouldAllowSecretReferenceExpressionInsideConcatenation() {
    // when
    final var collector = validate("=\"Bearer \" + camunda.secrets.token");

    // then
    verifyNoInteractions(collector);
  }

  @Test
  void shouldRejectSecretReferenceInsideObjectLiteral() {
    // when - the reference is a string literal nested in a constant object
    final var collector = validate("={\"auth\": \"camunda.secrets.token\"}");

    // then
    verify(collector).addError(eq(0), contains("camunda.secrets.token"));
  }

  @Test
  void shouldRejectSecretReferenceInsideListLiteral() {
    // when - the reference is a string literal nested in a constant list
    final var collector = validate("=[\"camunda.secrets.token\"]");

    // then
    verify(collector).addError(eq(0), contains("camunda.secrets.token"));
  }

  @Test
  void shouldAllowSecretReferenceExpressionInsideObjectLiteral() {
    // when - the reference is an expression path inside the object, not a literal
    final var collector = validate("={\"auth\": camunda.secrets.token}");

    // then
    verifyNoInteractions(collector);
  }

  @Test
  void shouldAllowClusterVariableExpression() {
    // when
    final var collector = validate("=camunda.vars.env.REGION");

    // then
    verifyNoInteractions(collector);
  }

  @Test
  void shouldAllowPlainStaticValue() {
    // when
    final var collector = validate("hello world");

    // then
    verifyNoInteractions(collector);
  }

  @Test
  void shouldAllowPlainStringLiteral() {
    // when
    final var collector = validate("=\"hello world\"");

    // then
    verifyNoInteractions(collector);
  }

  @Test
  void shouldIgnoreNullSource() {
    // when
    final var collector = validate(null);

    // then
    verifyNoInteractions(collector);
  }

  @Test
  void shouldReportEverySecretReferenceInAnObjectLiteral() {
    // when - two distinct references appear as literals in one object
    final var collector =
        validate("={\"a\": \"camunda.secrets.tokenA\", \"b\": \"camunda.secrets.tokenB\"}");

    // then - both are listed, not just the first
    verify(collector)
        .addError(
            eq(0),
            argThat(
                message ->
                    message.contains("camunda.secrets.tokenA")
                        && message.contains("camunda.secrets.tokenB")));
  }

  @Test
  void shouldRejectAndReportSecretReferenceWithUnicodeName() {
    // when - a name with non-ASCII letters
    final var collector = validate("=\"camunda.secrets.tökén\"");

    // then - matched and reported in full
    verify(collector).addError(eq(0), contains("camunda.secrets.tökén"));
  }

  @Test
  void shouldRejectSecretReferenceContainingDigits() {
    // when
    final var collector = validate("camunda.secrets.token_2");

    // then
    verify(collector).addError(eq(0), contains("camunda.secrets.token_2"));
  }

  private ValidationResultCollector validate(final String source) {
    final var element = mock(ZeebeInput.class);
    when(element.getSource()).thenReturn(source);
    final var collector = mock(ValidationResultCollector.class);
    sut.validate(element, collector);
    return collector;
  }
}

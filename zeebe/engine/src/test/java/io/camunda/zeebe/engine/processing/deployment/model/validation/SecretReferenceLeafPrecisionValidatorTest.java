/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

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

final class SecretReferenceLeafPrecisionValidatorTest {

  private final ExpressionLanguage expressionLanguage =
      ExpressionLanguageFactory.createExpressionLanguage(
          new ZeebeFeelEngineClock(InstantSource.system()));
  private final SecretReferenceLeafPrecisionValidator sut =
      new SecretReferenceLeafPrecisionValidator(expressionLanguage);

  @Test
  void shouldRejectSecretReferenceInsideListLiteral() {
    // when
    final var collector = validate("=[camunda.secrets.token]");

    // then
    verify(collector).addError(eq(0), contains("camunda.secrets.token"));
  }

  @Test
  void shouldRejectSecretReferenceInsideContextProducedByAnIfBranch() {
    // when
    final var collector = validate("=if true then {x: camunda.secrets.token} else null");

    // then
    verify(collector).addError(eq(0), contains("camunda.secrets.token"));
  }

  @Test
  void shouldAllowPlainSecretReferenceExpression() {
    // when
    final var collector = validate("=camunda.secrets.token");

    // then
    verifyNoInteractions(collector);
  }

  @Test
  void shouldAllowSecretReferenceInsideAContextLiteralAtTheRoot() {
    // when - descended precisely by SecretReference#collect, not through the fallback
    final var collector = validate("={x: camunda.secrets.token}");

    // then
    verifyNoInteractions(collector);
  }

  @Test
  void shouldAllowListLiteralWithoutASecretReference() {
    // when
    final var collector = validate("=[1, 2, 3]");

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
  void shouldIgnoreNullSource() {
    // when
    final var collector = validate(null);

    // then
    verifyNoInteractions(collector);
  }

  @Test
  void shouldIgnoreInvalidExpression() {
    // when - reported separately by the expression validator
    final var collector = validate("=this is not valid feel {{{");

    // then
    verifyNoInteractions(collector);
  }

  private ValidationResultCollector validate(final String source) {
    final var element = mock(ZeebeInput.class);
    when(element.getSource()).thenReturn(source);
    final var collector = mock(ValidationResultCollector.class);
    sut.validate(element, collector);
    return collector;
  }
}

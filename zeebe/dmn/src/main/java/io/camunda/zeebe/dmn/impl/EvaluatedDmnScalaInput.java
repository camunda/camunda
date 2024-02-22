/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.dmn.impl;

import io.camunda.zeebe.dmn.EvaluatedInput;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.camunda.dmn.Audit;
import org.camunda.dmn.parser.FeelExpression;
import org.camunda.dmn.parser.ParsedInput;
import org.camunda.feel.syntaxtree.Val;

public record EvaluatedDmnScalaInput(String inputId, String inputName, DirectBuffer inputValue)
    implements EvaluatedInput {

  private static final String MAX_EXPRESSION_LENGTH = "30";
  private static final String TRUNCATE_EXPRESSION_TEMPLATE = "%." + MAX_EXPRESSION_LENGTH + "s";

  public static EvaluatedDmnScalaInput of(
      final Audit.EvaluatedInput evaluatedInput, final Function<Val, DirectBuffer> converter) {
    final var input = evaluatedInput.input();
    final var inputValue = evaluatedInput.value();
    final var inputName = determineInputName(input);
    return new EvaluatedDmnScalaInput(input.id(), inputName, converter.apply(inputValue));
  }

  private static String determineInputName(final ParsedInput input) {
    final String inputName;
    if (input.name() != null) {
      inputName = input.name();
    } else if (input.expression() instanceof FeelExpression feelExpression) {
      inputName = truncateExpression(feelExpression.expression().text());
    } else {
      inputName = null;
    }

    return inputName;
  }

  private static String truncateExpression(final String text) {
    if (text == null) {
      return null;
    }
    return TRUNCATE_EXPRESSION_TEMPLATE.formatted(text);
  }
}

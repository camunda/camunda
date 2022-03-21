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
import org.camunda.feel.syntaxtree.Val;

public record EvaluatedDmnScalaInput(String inputId, String inputName, DirectBuffer inputValue)
    implements EvaluatedInput {

  public static EvaluatedDmnScalaInput of(
      final Audit.EvaluatedInput evaluatedInput, final Function<Val, DirectBuffer> converter) {
    final var input = evaluatedInput.input();
    final var inputValue = evaluatedInput.value();

    final String inputName;
    if (input.name() != null) {
      inputName = input.name();
    } else if (input.expression() instanceof FeelExpression feelExpression) {
      inputName = feelExpression.expression().text();
    } else {
      inputName = null;
    }

    return new EvaluatedDmnScalaInput(input.id(), inputName, converter.apply(inputValue));
  }
}

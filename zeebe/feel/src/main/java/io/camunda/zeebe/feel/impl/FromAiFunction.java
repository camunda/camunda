/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.feel.impl;

import java.util.List;
import org.camunda.feel.context.JavaFunction;
import org.camunda.feel.syntaxtree.Val;
import org.camunda.feel.syntaxtree.ValError;
import org.camunda.feel.syntaxtree.ValNull$;

public class FromAiFunction extends JavaFunction {
  public static final List<JavaFunction> INSTANCES =
      List.of(
          new FromAiFunction(List.of("value")),
          new FromAiFunction(List.of("value", "description")),
          new FromAiFunction(List.of("value", "description", "type")),
          new FromAiFunction(List.of("value", "description", "type", "schema")));

  public FromAiFunction(final List<String> params) {
    super(params, FromAiFunction::invoke);
  }

  private static Val invoke(final List<Val> args) {
    final var firstArg = !args.isEmpty() ? args.getFirst() : null;
    if (firstArg == null || firstArg instanceof ValNull$) {
      return new ValError(
          "fromAi function expected at least one parameter (value), but received null");
    }

    return firstArg;
  }
}

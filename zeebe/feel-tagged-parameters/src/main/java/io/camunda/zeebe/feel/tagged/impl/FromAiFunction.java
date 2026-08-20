/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.feel.tagged.impl;

import java.util.List;
import org.camunda.feel.context.JavaFunction;
import org.camunda.feel.syntaxtree.Val;

public class FromAiFunction extends JavaFunction {
  public static final String FUNCTION_NAME = "fromAi";

  public static final List<JavaFunction> INSTANCES =
      List.of(
          new FromAiFunction(List.of("value")),
          new FromAiFunction(List.of("value", "description")),
          new FromAiFunction(List.of("value", "description", "type")),
          new FromAiFunction(List.of("value", "description", "type", "schema")),
          new FromAiFunction(List.of("value", "description", "type", "schema", "options")));

  public FromAiFunction(final List<String> params) {
    super(params, FromAiFunction::invoke);
  }

  private static Val invoke(final List<Val> args) {
    return !args.isEmpty() ? args.getFirst() : null;
  }
}

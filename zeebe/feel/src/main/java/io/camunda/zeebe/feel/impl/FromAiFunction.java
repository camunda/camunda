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
import org.camunda.feel.syntaxtree.ValContext;
import org.camunda.feel.syntaxtree.ValError;
import org.camunda.feel.syntaxtree.ValString;

public class FromAiFunction extends JavaFunction {
  public static final List<JavaFunction> INSTANCES =
      List.of(
          new FromAiFunction(List.of("context", "name")),
          new FromAiFunction(List.of("context", "name", "description")),
          new FromAiFunction(List.of("context", "name", "description", "type")),
          new FromAiFunction(List.of("context", "name", "description", "type", "schema")));

  private static final MessagePackValueMapper MESSAGE_PACK_VALUE_MAPPER =
      new MessagePackValueMapper();

  public FromAiFunction(final List<String> params) {
    super(params, FromAiFunction::invoke);
  }

  private static Val invoke(final List<Val> args) {
    if (args.size() < 2) {
      return new ValError(
          "fromAi function expected at least two parameters, but found %s: '%s'"
              .formatted(args.size(), args));
    }

    final var context = args.getFirst();
    if (!(context instanceof final ValContext contextCtx)) {
      return new ValError(
          "fromAi function expected first parameter (context) to be a context, but found '%s'"
              .formatted(context.getClass().getName()));
    }

    final var name = args.get(1);
    if (!(name instanceof final ValString nameStr)) {
      return new ValError(
          "fromAi function expected second parameter (name) to be a string, but found '%s'"
              .formatted(name.getClass().getName()));
    }

    final var variable = contextCtx.context().variableProvider().getVariable(nameStr.value());
    if (variable.isEmpty()) {
      return new ValError(
          "fromAi function expected context to contain property '%s', but it was not found"
              .formatted(nameStr.value()));
    }

    return MESSAGE_PACK_VALUE_MAPPER.toVal(variable.get(), null).get();
  }
}

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

public abstract class FromAiFunctions {
  public static final List<JavaFunction> INSTANCES =
      List.of(
          new FromAiFunction2(),
          new FromAiFunction3(),
          new FromAiFunction4(),
          new FromAiFunction5());

  private static final MessagePackValueMapper MAPPER = new MessagePackValueMapper();

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

    return MAPPER.toVal(variable.get(), null).get();
  }

  public static class FromAiFunction2 extends JavaFunction {
    public FromAiFunction2() {
      super(List.of("context", "name"), FromAiFunctions::invoke);
    }
  }

  public static class FromAiFunction3 extends JavaFunction {
    public FromAiFunction3() {
      super(List.of("context", "name", "description"), FromAiFunctions::invoke);
    }
  }

  public static class FromAiFunction4 extends JavaFunction {
    public FromAiFunction4() {
      super(List.of("context", "name", "description", "type"), FromAiFunctions::invoke);
    }
  }

  public static class FromAiFunction5 extends JavaFunction {
    public FromAiFunction5() {
      super(List.of("context", "name", "description", "type", "schema"), FromAiFunctions::invoke);
    }
  }
}

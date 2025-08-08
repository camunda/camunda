/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.feel.impl;

import io.camunda.zeebe.feel.tagged.impl.FromAiFunction;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.camunda.feel.context.JavaFunction;
import org.camunda.feel.context.JavaFunctionProvider;
import org.camunda.feel.syntaxtree.Val;
import org.camunda.feel.syntaxtree.ValDayTimeDuration;
import org.camunda.feel.syntaxtree.ValError;
import org.camunda.feel.syntaxtree.ValNull$;
import org.camunda.feel.syntaxtree.ValNumber;
import org.camunda.feel.syntaxtree.ValString;
import org.camunda.feel.syntaxtree.ValYearMonthDuration;

public class FeelFunctionProvider extends JavaFunctionProvider {
  private static final Map<String, List<JavaFunction>> FUNCTIONS =
      Map.of(
          "cycle",
          List.of(CycleFunction.INSTANCE, CycleInfiniteFunction.INSTANCE),
          FromAiFunction.FUNCTION_NAME,
          FromAiFunction.INSTANCES);

  @Override
  public Optional<JavaFunction> resolveFunction(final String functionName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Collection<String> getFunctionNames() {
    return FUNCTIONS.keySet();
  }

  @Override
  public List<JavaFunction> resolveFunctions(final String functionName) {
    return FUNCTIONS.getOrDefault(functionName, List.of());
  }

  private static final class CycleFunction extends JavaFunction {
    public static final CycleFunction INSTANCE = new CycleFunction();

    public CycleFunction() {
      super(List.of("repetitions", "interval"), CycleFunction::invoke);
    }

    private static Val invoke(final List<Val> args) {
      if (args.size() != 2) {
        new ValError(
            "cycle function expected exactly two parameters, but found %s: '%s'"
                .formatted(args.size(), args));
      }
      return switch (args.get(0)) {
        case final ValNull$ ignored ->
            switch (args.get(1)) {
              case final ValDayTimeDuration duration -> new ValString("R/%s".formatted(duration));
              case final ValYearMonthDuration duration -> new ValString("R/%s".formatted(duration));
              default ->
                  new ValError(
                      "cycle function expected a repetitions (number) and an interval (duration) parameter, but found '%s'"
                          .formatted(args));
            };
        case final ValNumber repetitions ->
            switch (args.get(1)) {
              case final ValDayTimeDuration duration ->
                  new ValString("R%d/%S".formatted(repetitions.value().intValue(), duration));
              case final ValYearMonthDuration duration ->
                  new ValString("R%d/%S".formatted(repetitions.value().intValue(), duration));
              default ->
                  new ValError(
                      "cycle function expected a repetitions (number) and an interval (duration) parameter, but found '%s'"
                          .formatted(args));
            };
        default ->
            new ValError(
                "cycle function expected a repetitions (number) and an interval (duration) parameter, but found '%s'"
                    .formatted(args));
      };
    }
  }

  private static final class CycleInfiniteFunction extends JavaFunction {
    public static final CycleInfiniteFunction INSTANCE = new CycleInfiniteFunction();

    public CycleInfiniteFunction() {
      super(List.of("repetitions"), CycleInfiniteFunction::invoke);
    }

    private static Val invoke(final List<Val> args) {
      if (args.size() != 1) {
        new ValError(
            "cycle function expected exactly one interval (duration) parameter, but found %s: '%s'"
                .formatted(args.size(), args));
      }
      return switch (args.getFirst()) {
        case final ValDayTimeDuration duration -> new ValString("R/%s".formatted(duration));
        case final ValYearMonthDuration duration -> new ValString("R/%s".formatted(duration));
        case final ValError e -> e;
        default ->
            new ValError(
                "cycle function expected an interval (duration) parameter, but found '%s'"
                    .formatted(args.getFirst()));
      };
    }
  }
}

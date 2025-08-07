/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.adhocsubprocess.parameter;

import io.camunda.zeebe.engine.processing.adhocsubprocess.AdHocActivityMetadata.AdHocActivityParameter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.camunda.feel.api.EvaluationResult;
import org.camunda.feel.api.FeelEngineApi;
import org.camunda.feel.api.FeelEngineBuilder;
import org.camunda.feel.syntaxtree.ConstString;
import org.camunda.feel.syntaxtree.Exp;
import org.camunda.feel.syntaxtree.FunctionInvocation;
import org.camunda.feel.syntaxtree.NamedFunctionParameters;
import org.camunda.feel.syntaxtree.ParsedExpression;
import org.camunda.feel.syntaxtree.PositionalFunctionParameters;
import org.camunda.feel.syntaxtree.Ref;
import scala.jdk.javaapi.CollectionConverters;

class FromAiFeelFunctionCallParameterExtractor implements FeelFunctionCallParameterExtractor {

  private final FeelEngineApi feelEngineApi = FeelEngineBuilder.forJava().build();

  @Override
  public String functionName() {
    return "fromAi";
  }

  @Override
  public AdHocActivityParameter mapToParameter(final FunctionInvocation functionInvocation) {
    return switch (functionInvocation.params()) {
      case final PositionalFunctionParameters positionalFunctionParameters ->
          fromPositionalFunctionInvocationParams(
              CollectionConverters.asJava(positionalFunctionParameters.params()));

      case final NamedFunctionParameters namedFunctionParameters ->
          fromNamedFunctionInvocationParams(
              CollectionConverters.asJava(namedFunctionParameters.params()));

      default ->
          throw new IllegalArgumentException(
              "Unsupported fromAi() function invocation: " + functionInvocation.params());
    };
  }

  private AdHocActivityParameter fromPositionalFunctionInvocationParams(final List<Exp> params) {
    final Function<Integer, Exp> getParam =
        index -> (params.size() > index ? params.get(index) : null);

    return fromFunctionInvocationParams(
        getParam.apply(0),
        getParam.apply(1),
        getParam.apply(2),
        getParam.apply(3),
        getParam.apply(4));
  }

  private AdHocActivityParameter fromNamedFunctionInvocationParams(final Map<String, Exp> params) {
    return fromFunctionInvocationParams(
        params.get("value"),
        params.get("description"),
        params.get("type"),
        params.get("schema"),
        params.get("options"));
  }

  private AdHocActivityParameter fromFunctionInvocationParams(
      final Exp name, final Exp description, final Exp type, final Exp schema, final Exp options) {

    final var parameterName = parameterName(name);
    final var descriptionStr = evaluateToString(description, "description");
    final var typeStr = evaluateToString(type, "type");
    final var schemaMap = evaluateToMap(schema, "schema");
    final var optionsMap = evaluateToMap(options, "options");

    return new AdHocActivityParameter(
        parameterName, descriptionStr, typeStr, schemaMap, optionsMap);
  }

  private String parameterName(final Exp value) {
    if (!(value instanceof final Ref valueRef)) {
      throw new IllegalArgumentException(
          "Expected fromAi() parameter 'value' to be a reference (e.g. 'toolCall.customParameter'), but received %s."
              .formatted(
                  switch (value) {
                    case final ConstString stringResult ->
                        "string '%s'".formatted(stringResult.value());
                    default -> value;
                  }));
    }

    return valueRef.names().last();
  }

  private String evaluateToString(final Exp exp, final String parameterName) {
    if (exp == null) {
      return null;
    }

    final Object result = evaluate(exp, parameterName);
    if (!(result instanceof final String resultString)) {
      throw new IllegalArgumentException(
          "Expected fromAi() parameter '%s' to be a string, but received '%s'."
              .formatted(parameterName, result));
    }

    return resultString;
  }

  private Map<String, Object> evaluateToMap(final Exp exp, final String parameterName) {
    if (exp == null) {
      return null;
    }

    final Object result = evaluate(exp, parameterName);
    if (!(result instanceof final Map<?, ?> resultMap)) {
      throw new IllegalArgumentException(
          "Expected fromAi() parameter '%s' to be a map, but received '%s'."
              .formatted(parameterName, result));
    }

    return resultMap.entrySet().stream()
        .collect(
            Collectors.toMap(
                entry -> entry.getKey().toString(),
                entry -> (Object) entry.getValue(),
                (v1, v2) -> v2,
                LinkedHashMap::new));
  }

  private Object evaluate(final Exp exp, final String parameterName) {
    final EvaluationResult result =
        feelEngineApi.evaluate(new ParsedExpression(exp, ""), Collections.emptyMap());
    if (result.isFailure()) {
      throw new IllegalArgumentException(
          "Failed to evaluate expression for fromAi() parameter '%s': %s"
              .formatted(parameterName, result.failure().message()));
    }

    return result.result();
  }
}

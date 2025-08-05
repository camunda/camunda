/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.adhocsubprocess;

import io.camunda.zeebe.engine.processing.adhocsubprocess.AdHocActivityMetadata.AdHocActivityParameter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
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
import scala.Product;
import scala.jdk.javaapi.CollectionConverters;

public class AdHocActivityParameterExtractor {

  private static final FeelFunctionInvocationExtractor FROM_AI_FUNCTION_EXTRACTOR =
      new FeelFunctionInvocationExtractor("fromAi");

  private final FeelEngineApi feelEngineApi;

  public AdHocActivityParameterExtractor() {
    this(FeelEngineBuilder.forJava().build());
  }

  public AdHocActivityParameterExtractor(final FeelEngineApi feelEngineApi) {
    this.feelEngineApi = feelEngineApi;
  }

  public List<AdHocActivityParameter> extractParameters(final ParsedExpression expression) {
    final Set<FunctionInvocation> functionInvocations =
        FROM_AI_FUNCTION_EXTRACTOR.findMatchingFunctionInvocations(expression);

    return functionInvocations.stream().map(this::mapToParameter).toList();
  }

  private AdHocActivityParameter mapToParameter(final FunctionInvocation functionInvocation) {
    return switch (functionInvocation.params()) {
      case final PositionalFunctionParameters positionalFunctionParameters ->
          fromPositionalFunctionInvocationParams(
              CollectionConverters.asJava(positionalFunctionParameters.params()));

      case final NamedFunctionParameters namedFunctionParameters ->
          fromNamedFunctionInvocationParams(
              CollectionConverters.asJava(namedFunctionParameters.params()));

      default ->
          throw new RuntimeException(
              "Unsupported function invocation: " + functionInvocation.params());
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
      throw new RuntimeException(
          "Expected parameter 'value' to be a reference (e.g. 'toolCall.customParameter'), but received %s."
              .formatted(
                  switch (value) {
                    case final ConstString stringResult ->
                        "string '%s'".formatted(stringResult.value());
                    default -> value;
                  }));
    }

    if (valueRef.names() == null || valueRef.names().isEmpty()) {
      // e.g. toolCall.parameter
      throw new RuntimeException(
          "Expected parameter 'value' to be a reference with at least one segment, but received '%s'."
              .formatted(valueRef));
    }

    return valueRef.names().last();
  }

  private String evaluateToString(final Exp exp, final String parameterName) {
    if (exp == null) {
      return null;
    }

    final Object result = evaluate(exp, parameterName);
    if (!(result instanceof final String resultString)) {
      throw new RuntimeException(
          "Expected parameter '%s' to be a string, but received '%s'."
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
      throw new RuntimeException(
          "Expected parameter '%s' to be a map, but received '%s'."
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
      throw new RuntimeException(
          "Failed to evaluate expression for parameter '%s': %s"
              .formatted(parameterName, result.failure().message()));
    }

    return result.result();
  }

  private static class FeelFunctionInvocationExtractor {
    private final Predicate<FunctionInvocation> functionPredicate;

    FeelFunctionInvocationExtractor(final String functionName) {
      this(functionInvocation -> functionInvocation.function().equals(functionName));
    }

    FeelFunctionInvocationExtractor(final Predicate<FunctionInvocation> functionPredicate) {
      this.functionPredicate = functionPredicate;
    }

    public Set<FunctionInvocation> findMatchingFunctionInvocations(
        final ParsedExpression parsedExpression) {
      final var results =
          findMatchingFunctionInvocations(parsedExpression.expression(), new LinkedHashSet<>());
      return Collections.unmodifiableSet(results);
    }

    private Set<FunctionInvocation> findMatchingFunctionInvocations(
        final Object object, final Set<FunctionInvocation> functions) {
      if (object instanceof final FunctionInvocation functionInvocation
          && functionPredicate.test(functionInvocation)) {
        functions.add(functionInvocation);
        return functions;
      }

      if (!(object instanceof final Product product)) {
        return functions;
      }

      CollectionConverters.asJava(product.productIterator())
          .forEachRemaining(
              obj -> {
                if (obj instanceof final FunctionInvocation functionInvocation
                    && functionPredicate.test(functionInvocation)) {
                  functions.add(functionInvocation);
                } else {
                  findMatchingFunctionInvocations(obj, functions);
                }
              });

      return functions;
    }
  }
}

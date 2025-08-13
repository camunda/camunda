/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.feel.tagged.impl;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.camunda.feel.syntaxtree.ConstBool;
import org.camunda.feel.syntaxtree.ConstContext;
import org.camunda.feel.syntaxtree.ConstList;
import org.camunda.feel.syntaxtree.ConstNumber;
import org.camunda.feel.syntaxtree.ConstString;
import org.camunda.feel.syntaxtree.Exp;
import org.camunda.feel.syntaxtree.FunctionInvocation;
import org.camunda.feel.syntaxtree.NamedFunctionParameters;
import org.camunda.feel.syntaxtree.PositionalFunctionParameters;
import org.camunda.feel.syntaxtree.Ref;
import scala.jdk.javaapi.CollectionConverters;

public class FromAiTaggedParameterExtractor implements FunctionInvocationTaggedParameterExtractor {

  @Override
  public String functionName() {
    return FromAiFunction.FUNCTION_NAME;
  }

  @Override
  public TaggedParameter extract(final FunctionInvocation functionInvocation) {
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

  private TaggedParameter fromPositionalFunctionInvocationParams(final List<Exp> params) {
    final Function<Integer, Exp> getParam =
        index -> (params.size() > index ? params.get(index) : null);

    return fromFunctionInvocationParams(
        getParam.apply(0),
        getParam.apply(1),
        getParam.apply(2),
        getParam.apply(3),
        getParam.apply(4));
  }

  private TaggedParameter fromNamedFunctionInvocationParams(final Map<String, Exp> params) {
    return fromFunctionInvocationParams(
        params.get("value"),
        params.get("description"),
        params.get("type"),
        params.get("schema"),
        params.get("options"));
  }

  private TaggedParameter fromFunctionInvocationParams(
      final Exp name, final Exp description, final Exp type, final Exp schema, final Exp options) {

    final var parameterName = parameterName(name);
    final var descriptionStr = asString(description, "description");
    final var typeStr = asString(type, "type");
    final var schemaMap = asMap(schema, "schema");
    final var optionsMap = asMap(options, "options");

    return new TaggedParameter(parameterName, descriptionStr, typeStr, schemaMap, optionsMap);
  }

  private String parameterName(final Exp value) {
    if (!(value instanceof final Ref valueRef)) {
      throw new IllegalArgumentException(
          "Expected fromAi() parameter 'value' to be a reference (e.g. 'toolCall.customParameter'), but received %s."
              .formatted(
                  switch (value) {
                    case final ConstString stringResult ->
                        "string '%s'".formatted(stringResult.value());
                    default -> typeMismatchExceptionValue(value);
                  }));
    }

    return String.join(".", CollectionConverters.asJava(valueRef.names()));
  }

  private String asString(final Exp exp, final String parameterName) {
    if (exp == null) {
      return null;
    }

    if (!(exp instanceof final ConstString constString)) {
      throw new IllegalArgumentException(
          "Expected fromAi() parameter '%s' to be a string, but received '%s'."
              .formatted(parameterName, typeMismatchExceptionValue(exp)));
    }

    return constString.value();
  }

  private Map<String, Object> asMap(final Exp exp, final String parameterName) {
    if (exp == null) {
      return null;
    }

    if (!(exp instanceof final ConstContext constContext)) {
      throw new IllegalArgumentException(
          "Expected fromAi() parameter '%s' to be a context (map), but received '%s'."
              .formatted(parameterName, typeMismatchExceptionValue(exp)));
    }

    return convertContext(constContext);
  }

  private Object convertConstant(final Object object) {
    return switch (object) {
      case null -> null;
      case final ConstNumber constNumber -> constNumber.value();
      case final ConstBool constBool -> constBool.value();
      case final ConstString constString -> constString.value();

      case final ConstList constList ->
          CollectionConverters.asJava(constList.items()).stream()
              .map(this::convertConstant)
              .toList();

      case final ConstContext constContext -> convertContext(constContext);

      default ->
          throw new IllegalArgumentException(
              "Unsupported expression value in fromAi() function invocation: %s"
                  .formatted(object.getClass().getSimpleName()));
    };
  }

  private Map<String, Object> convertContext(final ConstContext constContext) {
    return CollectionConverters.asJava(constContext.entries()).stream()
        .collect(
            LinkedHashMap::new,
            (map, entry) ->
                map.put(
                    entry.productElement(0).toString(), convertConstant(entry.productElement(1))),
            HashMap::putAll);
  }

  private Object typeMismatchExceptionValue(final Exp exp) {
    return switch (exp) {
      case final ConstString constString -> constString.value();
      case final ConstNumber constNumber -> constNumber.value().toString();
      case final ConstBool constBool -> constBool.value();
      default -> exp;
    };
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.feel.tagged.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.camunda.feel.syntaxtree.FunctionInvocation;
import org.camunda.feel.syntaxtree.ParsedExpression;
import scala.Product;
import scala.jdk.javaapi.CollectionConverters;

/**
 * Extracts "tagged" parameters such as fromAi() into a structured format that can be used for
 * further processing, such as generating a JSON schema or documentation.
 *
 * <p>Delegating actual function call handling to individual {@link
 * FunctionInvocationTaggedParameterExtractor} implementations handling a specific function.
 */
public class TaggedParameterExtractor {

  private final Map<String, FunctionInvocationTaggedParameterExtractor> extractorsByFunctionName =
      new LinkedHashMap<>();

  public TaggedParameterExtractor() {
    this(List.of(new FromAiTaggedParameterExtractor()));
  }

  public TaggedParameterExtractor(
      final List<FunctionInvocationTaggedParameterExtractor> extractorsByFunctionName) {
    Objects.requireNonNull(extractorsByFunctionName, "Parameter extractors must not be null");
    extractorsByFunctionName.forEach(
        extractor -> this.extractorsByFunctionName.put(extractor.functionName(), extractor));
  }

  public List<TaggedParameter> extractParameters(final ParsedExpression parsedExpression) {
    final var extracted = extractParameters(parsedExpression.expression(), new ArrayList<>());
    return Collections.unmodifiableList(extracted);
  }

  private List<TaggedParameter> extractParameters(
      final Object object, final List<TaggedParameter> extracted) {
    if (isSupportedFunctionInvocation(object)) {
      processFunctionInvocation((FunctionInvocation) object, extracted);
      return extracted;
    }

    if (!(object instanceof final Product product)) {
      return extracted;
    }

    CollectionConverters.asJava(product.productIterator())
        .forEachRemaining(
            obj -> {
              if (isSupportedFunctionInvocation(obj)) {
                processFunctionInvocation((FunctionInvocation) obj, extracted);
              } else {
                extractParameters(obj, extracted);
              }
            });

    return extracted;
  }

  private boolean isSupportedFunctionInvocation(final Object object) {
    return object instanceof final FunctionInvocation functionInvocation
        && extractorsByFunctionName.containsKey(functionInvocation.function());
  }

  private void processFunctionInvocation(
      final FunctionInvocation functionInvocation, final List<TaggedParameter> extracted) {
    extracted.add(
        extractorsByFunctionName.get(functionInvocation.function()).extract(functionInvocation));
  }
}

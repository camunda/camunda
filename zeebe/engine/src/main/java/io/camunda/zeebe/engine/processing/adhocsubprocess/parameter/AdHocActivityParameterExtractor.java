/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.adhocsubprocess.parameter;

import io.camunda.zeebe.engine.processing.adhocsubprocess.AdHocActivityMetadata.AdHocActivityParameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.camunda.feel.syntaxtree.FunctionInvocation;
import org.camunda.feel.syntaxtree.ParsedExpression;
import scala.Product;
import scala.jdk.javaapi.CollectionConverters;

/**
 * Generic parameter extractor delegating actual function call handling to individual
 * FunctionCallParameterExtractors.
 */
public class AdHocActivityParameterExtractor {

  private final Map<String, FeelFunctionCallParameterExtractor> parameterExtractors =
      new LinkedHashMap<>();

  public AdHocActivityParameterExtractor() {
    registerParameterExtractor(new FromAiFeelFunctionCallParameterExtractor());
  }

  private void registerParameterExtractor(final FeelFunctionCallParameterExtractor extractor) {
    parameterExtractors.put(extractor.functionName(), extractor);
  }

  public List<AdHocActivityParameter> extractParameters(final ParsedExpression parsedExpression) {
    final var extracted = extractParameters(parsedExpression.expression(), new ArrayList<>());
    return Collections.unmodifiableList(extracted);
  }

  private List<AdHocActivityParameter> extractParameters(
      final Object object, final List<AdHocActivityParameter> extracted) {
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
        && parameterExtractors.containsKey(functionInvocation.function());
  }

  private void processFunctionInvocation(
      final FunctionInvocation functionInvocation, final List<AdHocActivityParameter> extracted) {
    extracted.add(
        parameterExtractors.get(functionInvocation.function()).mapToParameter(functionInvocation));
  }
}

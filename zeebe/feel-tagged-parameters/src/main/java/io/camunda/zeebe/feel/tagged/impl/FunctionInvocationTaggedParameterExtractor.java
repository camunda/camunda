/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.feel.tagged.impl;

import org.camunda.feel.syntaxtree.FunctionInvocation;

/** Function specific parameter extractor. */
public interface FunctionInvocationTaggedParameterExtractor {

  /** The function name this extractor is responsible for. */
  String functionName();

  /** Transform a function invocation into a {@link TaggedParameter}. */
  TaggedParameter extract(final FunctionInvocation functionInvocation);
}

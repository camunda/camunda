/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.el;

import java.util.Map;
import org.agrona.DirectBuffer;

public final class StaticEvaluationContext implements EvaluationContext {

  private final Map<String, DirectBuffer> context;

  private StaticEvaluationContext(final Map<String, DirectBuffer> context) {
    this.context = context;
  }

  @Override
  public DirectBuffer getVariable(final String variableName) {
    return context.get(variableName);
  }

  public static StaticEvaluationContext empty() {
    return new StaticEvaluationContext(Map.of());
  }

  public static StaticEvaluationContext of(final Map<String, DirectBuffer> context) {
    return new StaticEvaluationContext(context);
  }
}

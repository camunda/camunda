/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.property;

import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class PropertyAuthorizationEvaluatorRegistry {

  private final Map<AuthorizationResourceType, PropertyAuthorizationEvaluator> evaluators =
      new ConcurrentHashMap<>();

  public void register(final PropertyAuthorizationEvaluator evaluator) {
    evaluators.put(evaluator.resourceType(), evaluator);
  }

  public Optional<PropertyAuthorizationEvaluator> get(final AuthorizationResourceType type) {
    return Optional.ofNullable(evaluators.get(type));
  }
}

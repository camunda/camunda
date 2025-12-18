/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.authorization.property;

import io.camunda.zeebe.engine.processing.identity.authorization.property.evaluator.PropertyAuthorizationEvaluator;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public final class PropertyAuthorizationEvaluatorRegistry {

  private final Map<AuthorizationResourceType, PropertyAuthorizationEvaluator> evaluators =
      new EnumMap<>(AuthorizationResourceType.class);

  public PropertyAuthorizationEvaluatorRegistry register(
      final PropertyAuthorizationEvaluator evaluator) {
    evaluators.put(evaluator.resourceType(), evaluator);
    return this;
  }

  public Optional<PropertyAuthorizationEvaluator> get(
      final AuthorizationResourceType resourceType) {
    return Optional.ofNullable(evaluators.get(resourceType));
  }
}

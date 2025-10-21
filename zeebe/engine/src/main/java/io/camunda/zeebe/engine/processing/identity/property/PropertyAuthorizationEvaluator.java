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
import java.util.Set;

/** Evaluates property-based authorization for a resource type. */
public interface PropertyAuthorizationEvaluator {

  AuthorizationResourceType resourceType();

  /**
   * @param claims authorization claims map
   * @param resourceProperties runtime properties of resource instance
   * @return property names that match principal according to evaluator strategies
   */
  Set<String> matches(Map<String, Object> claims, Map<String, Object> resourceProperties);
}

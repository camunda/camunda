/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.authorization.property.evaluator;

import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.util.Map;
import java.util.Set;

/**
 * Evaluates property-based authorization for a specific resource type.
 *
 * <p>Property-based authorization allows granting access based on runtime attributes of a resource
 * rather than static resource IDs.
 */
public interface PropertyAuthorizationEvaluator {

  /**
   * @return the resource type this evaluator handles
   */
  AuthorizationResourceType resourceType();

  /**
   * Evaluates which resource properties the principal matches.
   *
   * @param claims authorization claims from the request
   * @param resourceProperties runtime properties of the resource instance
   * @return property names where the principal matches the resource value
   */
  Set<String> matches(Map<String, Object> claims, Map<String, Object> resourceProperties);
}

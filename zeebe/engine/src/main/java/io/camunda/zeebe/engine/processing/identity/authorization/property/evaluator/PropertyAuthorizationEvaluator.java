/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.authorization.property.evaluator;

import io.camunda.zeebe.engine.processing.identity.authorization.property.ResourceAuthorizationProperties;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.util.Map;
import java.util.Set;

/**
 * Evaluates property-based authorization for a specific resource type.
 *
 * <p>Property-based authorization allows granting access based on runtime attributes of a resource
 * rather than static resource IDs.
 *
 * @param <T> the type of resource properties this evaluator handles
 */
public interface PropertyAuthorizationEvaluator<T extends ResourceAuthorizationProperties> {

  /**
   * Returns the resource type this evaluator handles.
   *
   * @return the authorization resource type
   */
  AuthorizationResourceType resourceType();

  /**
   * Returns the class of properties this evaluator can process.
   *
   * <p>Used by the registry for type-safe dispatch.
   *
   * @return the properties class
   */
  Class<T> propertiesType();

  /**
   * Evaluates which resource properties the principal matches.
   *
   * @param claims authorization claims from the request
   * @param properties typed resource properties of the resource instance
   * @return property names where the principal matches the resource value.
   */
  Set<String> matches(Map<String, Object> claims, T properties);
}

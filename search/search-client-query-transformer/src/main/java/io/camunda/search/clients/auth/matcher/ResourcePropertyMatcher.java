/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.auth.matcher;

import io.camunda.security.auth.CamundaAuthentication;
import java.util.Set;

/**
 * Matches resource properties against authentication context to determine access.
 *
 * <p>Implementations extract property values from a resource and check if the authenticated user
 * matches any of the authorized properties.
 *
 * @param <T> the resource type
 */
public interface ResourcePropertyMatcher<T> {

  /**
   * Checks if the authenticated user matches any of the specified properties on the resource.
   *
   * @param resource the resource to check
   * @param authorizedPropertyNames the property names that can grant access
   * @param authentication the authentication context
   * @return true if the user matches any authorized property
   */
  boolean matches(
      T resource, Set<String> authorizedPropertyNames, CamundaAuthentication authentication);

  /** Returns the resource class this matcher handles. */
  Class<T> getResourceClass();
}

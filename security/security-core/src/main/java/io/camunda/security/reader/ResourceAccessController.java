/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.reader;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.SecurityContext;
import java.util.function.Function;

/**
 * A {@link ResourceAccessController} enhances any get and search with additional {@link
 * ResourceAccessChecks} to be applied executing them. However, any implementation of the {@link
 * ResourceAccessController} may decide to deny access immediately, and by that, to not execute the
 * read (i.e., get or search) at all and throw an exception instead.
 */
public interface ResourceAccessController {

  /**
   * Called before doing a get to retrieve a single resource.
   *
   * @param securityContext contains the {@link CamundaAuthentication} and the required {@link
   *     io.camunda.security.auth.Authorization authorization} to be checked.
   * @param resourceChecksApplier will be used to pass required @{@link ResourceAccessChecks} to the
   *     actual reader
   */
  <T> T doGet(
      SecurityContext securityContext, Function<ResourceAccessChecks, T> resourceChecksApplier);

  /**
   * Called before doing a search by query.
   *
   * @param securityContext contains the {@link CamundaAuthentication} and the required {@link
   *     io.camunda.security.auth.Authorization authorization} to be checked.
   * @param resourceChecksApplier will be used to pass required @{@link ResourceAccessChecks} to the
   *     actual reader
   */
  <T> T doSearch(
      SecurityContext securityContext, Function<ResourceAccessChecks, T> resourceChecksApplier);

  /**
   * Returns true if the given {@link io.camunda.security.auth.SecurityContext securityContext} is
   * supported by this {@link io.camunda.security.reader.ResourceAccessController} *
   */
  boolean supports(SecurityContext securityContext);

  /**
   * Returns true if the given {@link io.camunda.security.auth.CamundaAuthentication authentication}
   * is anonymous. *
   */
  default boolean isAnonymousAuthentication(final CamundaAuthentication authentication) {
    return authentication.isAnonymous();
  }
}

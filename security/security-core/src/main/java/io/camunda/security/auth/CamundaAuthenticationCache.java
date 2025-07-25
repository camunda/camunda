/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth;

public interface CamundaAuthenticationCache {

  /**
   * Returns to true, if {@link CamundaAuthenticationCache this} is responsible to cache a {@link
   * CamundaAuthentication} for the given {@link Object principal}.
   */
  boolean supports(final Object principal);

  /**
   * Puts the given {@link CamundaAuthentication} into the cache later retrieval, once
   * authentication has taken place. Used by <tt>ExceptionTranslationFilter</tt>.
   *
   * @param principal may be used as a cache key
   * @param authentication to be cached
   */
  void put(final Object principal, final CamundaAuthentication authentication);

  /**
   * Get cached {@link CamundaAuthentication} by given principal.
   *
   * @param principal used as a cache key to retrieve the {@link CamundaAuthentication}
   */
  CamundaAuthentication get(final Object principal);

  /**
   * Optionally remove an {@link CamundaAuthentication} from the cache.
   *
   * @param principal whose authentication should be removed
   */
  void remove(final Object principal);
}

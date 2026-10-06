/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.Either;

/**
 * Decides whether a user may access the Optimize component. One implementation per edition: CCSM
 * asks Management Identity for the Optimize permission, CCSaaS reads the organization role from the
 * token claims. It is asked on every request, so access ends when the grant is revoked.
 *
 * <p>An implementation that verifies the access token also answers right when the token has
 * expired: the webapp chain renews it, and denying here would lock out a user for a reason that is
 * not about their permissions. A revoked user therefore keeps API access until the token is
 * renewed, at most for its remaining lifetime, and is denied on the next web app request. Any other
 * verification failure is a denial.
 */
public interface OptimizeComponentAccessPolicy {

  /**
   * Requires the request of the current thread to be bound to {@code RequestContextHolder} and to
   * carry the HTTP session, because the CCSM implementation reads the session's access token from
   * it. Inside a security filter chain {@link OptimizeRequestContextBindingFilter} establishes that
   * binding.
   *
   * @param authentication the authentication of the current request
   * @return {@link Either#right(Object) right(null)} when the request may proceed, {@link
   *     Either#left(Object) left(reason)} carrying the reason for the denial otherwise
   */
  Either<String, Void> checkAccess(CamundaAuthentication authentication);
}

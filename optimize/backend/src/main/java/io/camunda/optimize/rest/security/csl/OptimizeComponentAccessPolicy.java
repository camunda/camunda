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
 * <p>Implementations answer right not only when access is granted but also when the decision cannot
 * be made, for example because the access token can no longer be verified: the security chain
 * rejects such a request on its own terms, and denying it here would lock out a user for a reason
 * that is not about their permissions.
 */
public interface OptimizeComponentAccessPolicy {

  /**
   * @param authentication the authentication of the current request
   * @return {@link Either#right(Object) right(null)} when the request may proceed, {@link
   *     Either#left(Object) left(reason)} carrying the reason for the denial otherwise
   */
  Either<String, Void> checkAccess(CamundaAuthentication authentication);
}

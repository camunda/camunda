/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth;

/**
 * Provides the current {@link CamundaAuthentication} representing the authentication context for a
 * user, client, or anonymous principal.
 */
public interface CamundaAuthenticationProvider {

  /**
   * Returns the current {@link CamundaAuthentication} representing the authentication context for a
   * user, client, or anonymous user.
   *
   * <p>The returned {@link CamundaAuthentication} may represent an anonymous user ({@code
   * authenticatedUsername} and {@code authenticatedClientId} will be null and {@code claims will
   * contain an entry of key:Authorization.AUTHORIZED_ANONYMOUS_USER value:true}) or a unique
   * principal (either {@code authenticatedUsername} or {@code authenticatedClientId} will be set,
   * but never both)
   *
   * @return the current {@link CamundaAuthentication}
   */
  CamundaAuthentication getCamundaAuthentication();

  default CamundaAuthentication getAnonymousCamundaAuthentication() {
    return CamundaAuthentication.anonymous();
  }
}

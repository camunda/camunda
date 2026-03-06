/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.spi;

import io.camunda.auth.domain.model.CamundaAuthentication;

/**
 * Provides the current {@link CamundaAuthentication} representing the authentication context for a
 * user, client, or anonymous principal.
 */
public interface CamundaAuthenticationProvider {

  /**
   * Returns the current {@link CamundaAuthentication}.
   *
   * @return the current authentication context
   */
  CamundaAuthentication getCamundaAuthentication();

  default CamundaAuthentication getAnonymousCamundaAuthentication() {
    return CamundaAuthentication.anonymous();
  }
}

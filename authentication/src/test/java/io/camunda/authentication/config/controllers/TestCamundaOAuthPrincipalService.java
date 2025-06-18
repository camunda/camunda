/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config.controllers;

import io.camunda.authentication.CamundaOAuthPrincipalService;
import io.camunda.authentication.entity.OAuthContext;
import java.util.Map;

public class TestCamundaOAuthPrincipalService implements CamundaOAuthPrincipalService {

  @Override
  public OAuthContext loadOAuthContext(Map<String, Object> claims) {
    return new OAuthContext(null, null);
  }
}

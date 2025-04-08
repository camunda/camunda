/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.entity;

import java.util.Map;

public interface CamundaOAuthPrincipal extends CamundaPrincipal {
  OAuthContext getOAuthContext();

  Map<String, Object> getClaims();
}

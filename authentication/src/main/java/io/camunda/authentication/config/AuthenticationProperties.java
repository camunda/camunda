/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

public final class AuthenticationProperties {
  public static final String METHOD = "camunda.security.authentication.method";
  public static final String API_UNPROTECTED =
      "camunda.security.authentication.basic.allow-unauthenticated-api-access";

  private AuthenticationProperties() {}
}

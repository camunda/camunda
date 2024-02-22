/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.auth.impl;

public class Authorization {

  public static final String AUTHORIZED_TENANTS = "authorized_tenants";

  public static JwtAuthorizationEncoder jwtEncoder() {
    return new JwtAuthorizationEncoder();
  }

  public static JwtAuthorizationDecoder jwtDecoder(final String jwtToken) {
    return new JwtAuthorizationDecoder(jwtToken);
  }
}

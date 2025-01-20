/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

import io.camunda.security.entity.AuthenticationMethod;

public class SecurityConfigurations {
  public static SecurityConfiguration unauthenticated() {
    final SecurityConfiguration securityConfiguration = new SecurityConfiguration();
    final AuthenticationConfiguration authenticationConfiguration =
        securityConfiguration.getAuthentication();
    authenticationConfiguration.setMethod(AuthenticationMethod.BASIC);
    authenticationConfiguration.getBasic().setAllowUnauthenticatedApiAccess(true);
    return securityConfiguration;
  }
}

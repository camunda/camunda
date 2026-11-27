/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import io.camunda.security.configuration.AuthenticationConfiguration;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/** Reads from the properties whether Camunda manages groups, before any bean exists. */
public final class CamundaGroupsMode {

  private static final String AUTHENTICATION_PROPERTY = "camunda.security.authentication";

  private CamundaGroupsMode() {}

  /**
   * @return whether the groups of at least one OIDC provider are managed in Camunda, which is when
   *     that provider has no groups claim. The Groups API is only available then. The properties
   *     are bound with Spring Boot's relaxed binding, so any spelling of a key works.
   */
  public static boolean isCamundaGroupsEnabled(final Environment environment) {
    final var authentication =
        Binder.get(environment)
            .bind(AUTHENTICATION_PROPERTY, AuthenticationConfiguration.class)
            .orElseGet(AuthenticationConfiguration::new);
    return authentication.isCamundaGroupsEnabled();
  }
}

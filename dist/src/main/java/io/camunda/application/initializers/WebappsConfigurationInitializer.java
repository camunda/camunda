/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.initializers;

import static io.camunda.application.Profile.IDENTITY;
import static io.camunda.application.Profile.OPERATE;
import static io.camunda.application.Profile.TASKLIST;
import static io.camunda.authentication.config.AuthenticationProperties.METHOD;

import io.camunda.authentication.config.WebSecurityConfig;
import io.camunda.security.entity.AuthenticationMethod;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;
import org.springframework.boot.env.DefaultPropertiesPropertySource;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public class WebappsConfigurationInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  public static final String CAMUNDA_WEBAPPS_ENABLED_PROPERTY = "camunda.webapps.enabled";
  private static final String CAMUNDA_WEBAPPS_DEFAULT_APP_PROPERTY = "camunda.webapps.default-app";
  private static final String CAMUNDA_WEBAPPS_LOGIN_DELEGATED_PROPERTY =
      "camunda.webapps.login-delegated";
  private static final String SERVER_SERVLET_SESSION_COOKIE_NAME_PROPERTY =
      "server.servlet.session.cookie.name";
  private static final Set<String> WEBAPPS_PROFILES =
      Set.of(OPERATE.getId(), TASKLIST.getId(), IDENTITY.getId());

  @Override
  public void initialize(final ConfigurableApplicationContext context) {
    final var environment = context.getEnvironment();
    final var propertySources = environment.getPropertySources();
    final var activeProfiles = Arrays.asList(environment.getActiveProfiles());

    if (activeProfiles.stream().anyMatch(WEBAPPS_PROFILES::contains)) {
      final var propertyMap = new HashMap<String, Object>();
      propertyMap.put(CAMUNDA_WEBAPPS_ENABLED_PROPERTY, true);
      if (activeProfiles.contains(OPERATE.getId())) {
        propertyMap.put(CAMUNDA_WEBAPPS_DEFAULT_APP_PROPERTY, OPERATE.getId());
      } else if (activeProfiles.contains(TASKLIST.getId())) {
        propertyMap.put(CAMUNDA_WEBAPPS_DEFAULT_APP_PROPERTY, TASKLIST.getId());
      } else if (activeProfiles.contains(IDENTITY.getId())) {
        propertyMap.put(CAMUNDA_WEBAPPS_DEFAULT_APP_PROPERTY, IDENTITY.getId());
      }
      propertyMap.put(CAMUNDA_WEBAPPS_LOGIN_DELEGATED_PROPERTY, isLoginDelegated(context));
      propertyMap.put(
          SERVER_SERVLET_SESSION_COOKIE_NAME_PROPERTY, WebSecurityConfig.SESSION_COOKIE);
      DefaultPropertiesPropertySource.addOrMerge(propertyMap, propertySources);
    }
  }

  private boolean isLoginDelegated(final ConfigurableApplicationContext context) {
    final var authenticationMethodProperty = context.getEnvironment().getProperty(METHOD);
    final var authenticationMethod = AuthenticationMethod.parse(authenticationMethodProperty);
    return authenticationMethod.isPresent()
        && AuthenticationMethod.OIDC.equals(authenticationMethod.get());
  }
}

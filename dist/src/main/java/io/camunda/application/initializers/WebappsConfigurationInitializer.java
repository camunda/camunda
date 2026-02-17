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
import static io.camunda.application.Profile.STANDALONE;
import static io.camunda.application.Profile.TASKLIST;
import static io.camunda.authentication.config.AuthenticationProperties.METHOD;

import io.camunda.authentication.config.WebSecurityConfig;
import io.camunda.security.entity.AuthenticationMethod;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
  private static final String RESOURCES_LOCATION_PROPERTY = "spring.web.resources.static-locations";
  private static final String DEFAULT_RESOURCES_LOCATION = "classpath:/META-INF/resources/";
  private static final String AUTHORIZATIONS_ENABLED_PROPERTY =
      "camunda.security.authorizations.enabled";
  private static final String MULTITENANCY_CHECKSENABLED_PROPERTY =
      "camunda.security.multiTenancy.checksEnabled";

  @Override
  public void initialize(final ConfigurableApplicationContext context) {
    final var environment = context.getEnvironment();
    final var propertySources = environment.getPropertySources();
    final var activeProfiles = Arrays.asList(environment.getActiveProfiles());
    final var propertyMap = new HashMap<String, Object>();

    if (activeProfiles.stream().anyMatch(WEBAPPS_PROFILES::contains)) {
      propertyMap.put(CAMUNDA_WEBAPPS_ENABLED_PROPERTY, true);

      propertyMap.put("spring.web.resources.add-mappings", true);
      propertyMap.put("spring.thymeleaf.check-template-location", true);
      propertyMap.put("spring.thymeleaf.prefix", DEFAULT_RESOURCES_LOCATION);

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
    }

    final Set<String> locations = new HashSet<>();
    locations.add(DEFAULT_RESOURCES_LOCATION);

    // Tasklist Properties

    if (activeProfiles.contains(TASKLIST.getId())) {
      locations.add(DEFAULT_RESOURCES_LOCATION + "tasklist/");
      if (activeProfiles.contains(STANDALONE.getId())) {
        propertyMap.putAll(
            Map.of(
                AUTHORIZATIONS_ENABLED_PROPERTY,
                "${camunda.tasklist.identity.resourcePermissionsEnabled:false}",
                MULTITENANCY_CHECKSENABLED_PROPERTY,
                "${camunda.tasklist.multiTenancy.enabled:false}"));
      }
    }

    // Operate Properties

    if (activeProfiles.contains(OPERATE.getId())) {
      locations.add(DEFAULT_RESOURCES_LOCATION + "operate/");
      if (activeProfiles.contains(STANDALONE.getId())) {
        propertyMap.putAll(
            Map.of(
                AUTHORIZATIONS_ENABLED_PROPERTY,
                "${camunda.operate.identity.resourcePermissionsEnabled:false}",
                MULTITENANCY_CHECKSENABLED_PROPERTY,
                "${camunda.operate.multiTenancy.enabled:false}"));
      }
    }

    // Identity Properties

    if (activeProfiles.contains(IDENTITY.getId())) {
      locations.add(DEFAULT_RESOURCES_LOCATION + "identity/");
    }

    // Store locations and merge everything

    propertyMap.put(RESOURCES_LOCATION_PROPERTY, locations);
    DefaultPropertiesPropertySource.addOrMerge(propertyMap, propertySources);
  }

  private boolean isLoginDelegated(final ConfigurableApplicationContext context) {
    final var authenticationMethodProperty = context.getEnvironment().getProperty(METHOD);
    final var authenticationMethod = AuthenticationMethod.parse(authenticationMethodProperty);
    return authenticationMethod.isPresent()
        && AuthenticationMethod.OIDC.equals(authenticationMethod.get());
  }
}

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
import static io.camunda.application.initializers.PropertiesHelper.loadListProperty;
import static io.camunda.authentication.config.AuthenticationProperties.METHOD;

import io.camunda.authentication.config.WebSecurityConfig;
import io.camunda.security.entity.AuthenticationMethod;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.env.DefaultPropertiesPropertySource;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

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

    final Map<String, Object> properties = new HashMap<>();
    final Set<String> locations = loadListProperty(environment, RESOURCES_LOCATION_PROPERTY);
    locations.add("classpath:/META-INF/resources/");

    // Tasklist Properties

    boolean tasklistIncluded = false;
    if (activeProfiles.contains(TASKLIST.getId())) {
      tasklistIncluded = true;
      locations.add("classpath:/META-INF/resources/tasklist/");
    }

    // Operate Properties

    boolean operateIncluded = false;
    if (activeProfiles.contains(OPERATE.getId())) {
      operateIncluded = true;
      locations.add("classpath:/META-INF/resources/operate/");
    }

    // Shared Properties

    if ((operateIncluded || tasklistIncluded) && activeProfiles.contains(STANDALONE.getId())) {
      properties.putAll(
          Map.of(
              "camunda.security.authorizations.enabled",
              "${camunda.tasklist.identity.resourcePermissionsEnabled:false}",
              "camunda.security.multiTenancy.checksEnabled",
              "${camunda.tasklist.multiTenancy.enabled:false}"));
    }

    if (activeProfiles.stream().anyMatch(WEBAPPS_PROFILES::contains)) {
      properties.putAll(
          Map.of(
              "spring.web.resources.add-mappings", "true",
              "spring.thymeleaf.check-template-location", "true",
              "spring.thymeleaf.prefix", "classpath:/META-INF/resources/"));
    }

    // Identity Properties

    if (activeProfiles.contains(IDENTITY.getId())) {
      locations.add("classpath:/META-INF/resources/identity/");
    }

    // Add everything

    properties.put(RESOURCES_LOCATION_PROPERTY, String.join(",", locations));
    environment
        .getPropertySources()
        .addFirst( // NOTE: addFirst is necessary
            new MapPropertySource("webappsResourcesProperties", properties));
  }

  private boolean isLoginDelegated(final ConfigurableApplicationContext context) {
    final var authenticationMethodProperty = context.getEnvironment().getProperty(METHOD);
    final var authenticationMethod = AuthenticationMethod.parse(authenticationMethodProperty);
    return authenticationMethod.isPresent()
        && AuthenticationMethod.OIDC.equals(authenticationMethod.get());
  }
}

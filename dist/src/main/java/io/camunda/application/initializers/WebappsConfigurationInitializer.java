/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.initializers;

import static io.camunda.application.Profile.ADMIN;
import static io.camunda.application.Profile.IDENTITY;
import static io.camunda.application.Profile.OPERATE;
import static io.camunda.application.Profile.STANDALONE;
import static io.camunda.application.Profile.TASKLIST;
import static io.camunda.authentication.config.AuthenticationProperties.METHOD;

import io.camunda.configuration.helpers.WebappsHelper;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.security.spring.security.CamundaSecurityFilterChainConstants;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.env.DefaultPropertiesPropertySource;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.StringUtils;

public class WebappsConfigurationInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  public static final String CAMUNDA_WEBAPPS_ENABLED_PROPERTY = "camunda.webapps.enabled";
  private static final Set<String> WEBAPPS_PROFILES =
      Set.of(OPERATE.getId(), TASKLIST.getId(), IDENTITY.getId(), ADMIN.getId());
  private static final String DEFAULT_RESOURCES_LOCATION = "classpath:/META-INF/resources/";
  private static final String OPERATE_TASKLIST_URL_PROPERTY = "camunda.operate.tasklist-url";
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

      propertyMap.put("camunda.webapps.login-delegated", isLoginDelegated(context));
      propertyMap.put(
          "server.servlet.session.cookie.name", CamundaSecurityFilterChainConstants.SESSION_COOKIE);
    }

    // locations and home page

    final Set<String> locations = new HashSet<>();
    locations.add(DEFAULT_RESOURCES_LOCATION);
    String defaultWebapp = null;

    // Operate Properties

    if (activeProfiles.contains(OPERATE.getId())) {
      if (WebappsHelper.isOperateUiEnabled(environment)) {
        locations.add(DEFAULT_RESOURCES_LOCATION + "operate/");
        defaultWebapp = OPERATE.getId();
      }

      if (activeProfiles.contains(STANDALONE.getId())) {
        propertyMap.putAll(
            Map.of(
                AUTHORIZATIONS_ENABLED_PROPERTY,
                "${camunda.operate.identity.resourcePermissionsEnabled:false}",
                MULTITENANCY_CHECKSENABLED_PROPERTY,
                "${camunda.operate.multiTenancy.enabled:false}"));
      }
    }

    // Tasklist Properties

    if (activeProfiles.contains(TASKLIST.getId())) {
      if (WebappsHelper.isTasklistUiEnabled(environment)) {
        locations.add(DEFAULT_RESOURCES_LOCATION + "webapp/");
        if (defaultWebapp == null) {
          defaultWebapp = TASKLIST.getId();
        }

        // Operate links to Tasklist only when both UIs are served by this application; as a
        // default property source, an explicit camunda.operate.tasklist-url still takes precedence.
        // Spring Boot treats a context path of "/" as root, so a plain join would give //tasklist
        if (activeProfiles.contains(OPERATE.getId())
            && WebappsHelper.isOperateUiEnabled(environment)) {
          propertyMap.put(
              OPERATE_TASKLIST_URL_PROPERTY,
              operateTasklistUrl(environment.getProperty("server.servlet.context-path")));
        }
      }
    }

    // Identity/Admin Properties

    if ((activeProfiles.contains(IDENTITY.getId()) || activeProfiles.contains(ADMIN.getId()))
        && WebappsHelper.isIdentityUiEnabled(environment)) {
      locations.add(DEFAULT_RESOURCES_LOCATION + "identity/");
      locations.add(DEFAULT_RESOURCES_LOCATION + "admin/");
      if (defaultWebapp == null) {
        defaultWebapp = ADMIN.getId();
      }
    }

    // Store locations, default homepage and merge everything

    propertyMap.put("spring.web.resources.static-locations", locations);
    if (defaultWebapp != null) {
      propertyMap.put("camunda.webapps.default-app", defaultWebapp);
    }
    DefaultPropertiesPropertySource.addOrMerge(propertyMap, propertySources);
  }

  private static String operateTasklistUrl(@Nullable final String contextPath) {
    if (contextPath == null) {
      return "/tasklist";
    }
    return StringUtils.trimTrailingCharacter(contextPath.strip(), '/') + "/tasklist";
  }

  private boolean isLoginDelegated(final ConfigurableApplicationContext context) {
    final var authenticationMethodProperty = context.getEnvironment().getProperty(METHOD);
    final var authenticationMethod = AuthenticationMethod.parse(authenticationMethodProperty);
    return AuthenticationMethod.OIDC.equals(authenticationMethod);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import static org.springframework.core.env.AbstractEnvironment.ACTIVE_PROFILES_PROPERTY_NAME;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.ConfigurableBootstrapContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public class ModesAndProfilesProcessor implements SpringApplicationRunListener {

  private static final String CAMUNDA_MODE_PROPERTY = "camunda.mode";
  private static final String CAMUNDA_INSECURE_PROPERTY = "camunda.insecure";
  private static final String CAMUNDA_DEVELOPMENT_PROPERTY = "camunda.development";

  private static final Set<String> VALID_MODES = Set.of("all-in-one", "broker", "gateway");

  private static final String SPRING_PROFILES_ACTIVE_PROPERTY = ACTIVE_PROFILES_PROPERTY_NAME;
  private static final Set<String> DEFAULT_PROFILES =
      Set.of(
          Profile.OPERATE.getId(),
          Profile.TASKLIST.getId(),
          Profile.BROKER.getId(),
          Profile.IDENTITY.getId(),
          Profile.CONSOLIDATED_AUTH.getId());

  private ConfigurableEnvironment environment;
  private final boolean isStandaloneCamunda;
  private final SpringApplication application;

  public ModesAndProfilesProcessor(SpringApplication application, String[] args) {
    this.application = application;

    if (application.getMainApplicationClass() == null) {
      this.isStandaloneCamunda = false;
    } else {
      this.isStandaloneCamunda =
          "StandaloneCamunda".equals(application.getMainApplicationClass().getSimpleName());
    }
  }

  @Override
  public void environmentPrepared(
      ConfigurableBootstrapContext bootstrapContext, ConfigurableEnvironment environment) {
    this.environment = environment;

    application.setLogStartupInfo(false);
    if (getMode() != null && !getMode().isBlank() && isStandaloneCamunda) {
      configureWithMode();
    } else {
      configureWithProfiles();
    }
  }

  private void configureWithMode() {
    System.out.println("Started Camunda using mode: " + getMode().toUpperCase());

    if (isInsecure()) {
      setProperty("zeebe.broker.gateway.security.enabled", "false", true); // embedded gateway
      setProperty("zeebe.gateway.security.enabled", "false", true); // dedicated gateway
      setProperty("camunda.security.authentication.unprotected-api", "true", true);
      setProperty("camunda.security.authentication.authorizations.enabled", "false", true);
    }

    switch (getMode().toLowerCase()) {
      case "broker" -> {
        configureProfilesForBrokerMode();
        setProperty("zeebe.broker.gateway.enable", "false", true);
        setProperty("camunda.webapps.enabled", "false", true);
        // setProperty("spring.main.web-application-type", "none", true); // 8080 doesn't open at
        // all
      }
      case "gateway" -> {
        configureProfilesForGatewayMode();
        setProperty("camunda.webapps.enabled", "true");
      }
      case "all-in-one" -> {
        configureProfilesForAllInOneMode();
        setProperty("zeebe.broker.gateway.enable", "true", true);
        setProperty("camunda.webapps.enabled", "true");
      }
      default -> {
        throw new IllegalStateException(
            "Invalid mode: " + getMode().toLowerCase() + ". Valid modes are: " + VALID_MODES);
      }
    }
  }

  private void setupActiveProfiles(final Set<String> profiles) {
    // tell Spring directly
    environment.setActiveProfiles(profiles.toArray(new String[0]));

    // also expose as property for consistency / logging / downstream readers
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "camundaActiveProfiles",
                Map.of(ACTIVE_PROFILES_PROPERTY_NAME, String.join(",", profiles))));
  }

  private void configureWithProfiles() {
    // no-op: we use the configuration passed as input
    System.out.println(
        "Started Camunda using profiles: " + String.join(",", environment.getActiveProfiles()));
  }

  private String getMode() {
    // NOTE: This listener happens before the initialization of the context, hence we cannot simply
    //  wire the UnifiedConfiguration object in here, as it is not created yet. We need to use
    //  the environment directly to read the property.
    return environment.getProperty(CAMUNDA_MODE_PROPERTY);
  }

  private boolean isInsecure() {
    return Boolean.parseBoolean(environment.getProperty(CAMUNDA_INSECURE_PROPERTY, "false"));
  }

  private boolean isDevelopment() {
    return Boolean.parseBoolean(environment.getProperty(CAMUNDA_DEVELOPMENT_PROPERTY, "false"));
  }

  private void setProperty(final String key, final String value, final boolean highPriority) {
    final MapPropertySource propertySource = new MapPropertySource("modeProps", Map.of(key, value));
    if (highPriority) {
      environment.getSystemProperties().put(key, value);
      environment.getPropertySources().addFirst(propertySource);
    } else {
      environment.getPropertySources().addLast(propertySource);
    }
  }

  private void setProperty(final String key, final String value) {
    setProperty(key, value, false);
  }

  private void configureProfilesForAllInOneMode() {
    final Set<String> profiles =
        new HashSet<>(
            Set.of(
                Profile.BROKER.getId(),
                Profile.TASKLIST.getId(),
                Profile.OPERATE.getId(),
                Profile.IDENTITY.getId(),
                Profile.CONSOLIDATED_AUTH.getId()));

    if (isDevelopment()) {
      profiles.add(Profile.DEVELOPMENT.getId());
    }

    setupActiveProfiles(profiles);
  }

  private void configureProfilesForBrokerMode() {
    final Set<String> profiles =
        new HashSet<>(Set.of(Profile.BROKER.getId(), Profile.STANDALONE.getId()));

    if (isDevelopment()) {
      profiles.add(Profile.DEVELOPMENT.getId());
    }

    setupActiveProfiles(profiles);
  }

  private void configureProfilesForGatewayMode() {
    final Set<String> profiles =
        new HashSet<>(
            Set.of(
                Profile.GATEWAY.getId(),
                Profile.OPERATE.getId(),
                Profile.IDENTITY.getId(),
                Profile.TASKLIST.getId()));

    if (isDevelopment()) {
      profiles.add(Profile.DEVELOPMENT.getId());
    }

    setupActiveProfiles(profiles);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

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

    if (getMode() != null && !getMode().isBlank() && isStandaloneCamunda) {
      configureWithMode();
    } else {
      configureWithProfiles();
    }
  }

  private void configureWithMode() {
    application.setLogStartupInfo(false);
    System.out.println("Using mode: " + getMode().toUpperCase());

    if (isInsecure()) {
      setProperty("zeebe.broker.gateway.security.enabled", "false", true);
      setProperty("camunda.security.authentication.unprotected-api", "true", true);
      setProperty("camunda.security.authentication.authorizations.enabled", "false", true);
    }

    switch (getMode()) {
      case "broker" -> {
        configureProfilesForBrokerMode();
        setProperty("zeebe.broker.gateway.enable", "false", true);
        setProperty("camunda.webapps.enabled", "false", true);
        setProperty("spring.main.web-application-type", "none", true); // 8080 doesn't open at all
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

  private void configureWithProfiles() {
    if (environment.getActiveProfiles().length == 0) {
      environment.setActiveProfiles(DEFAULT_PROFILES.toArray(new String[0]));
    }
  }

  private String getMode() {
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

    environment.setActiveProfiles(profiles.toArray(new String[0]));
  }

  private void configureProfilesForBrokerMode() {
    final Set<String> profiles =
        new HashSet<>(Set.of(Profile.BROKER.getId(), Profile.STANDALONE.getId()));

    environment.setActiveProfiles(profiles.toArray(new String[0]));
  }

  private void configureProfilesForGatewayMode() {
    final Set<String> profiles =
        new HashSet<>(
            Set.of(
                Profile.GATEWAY.getId(),
                Profile.OPERATE.getId(),
                Profile.IDENTITY.getId(),
                Profile.TASKLIST.getId()));

    environment.setActiveProfiles(profiles.toArray(new String[0]));
  }
}

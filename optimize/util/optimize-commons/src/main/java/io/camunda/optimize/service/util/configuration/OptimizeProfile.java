/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.CCSM_PROFILE;
import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.CLOUD_PROFILE;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum OptimizeProfile {
  CCSM(CCSM_PROFILE),
  CLOUD(CLOUD_PROFILE);

  private final String id;

  private OptimizeProfile(final String id) {
    this.id = id;
  }

  @JsonValue
  public String getId() {
    return name().toLowerCase(Locale.ENGLISH);
  }

  public static OptimizeProfile toProfile(final String profileString) {
    return valueOf(profileString.toUpperCase());
  }
}

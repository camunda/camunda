/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.j2objc.annotations.Property;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class OnboardingConfiguration {
  @Property("enabled")
  private boolean enabled;

  @Property("appCuesScriptUrl")
  private String appCuesScriptUrl;

  @Property("scheduleProcessOnboardingChecks")
  private boolean scheduleProcessOnboardingChecks;

  @Property("enableOnboardingEmails")
  private boolean enableOnboardingEmails;

  @Property("intervalForCheckingTriggerForOnboardingEmails")
  private int intervalForCheckingTriggerForOnboardingEmails;

  @JsonProperty("properties")
  private Properties properties;

  @AllArgsConstructor
  @Data
  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  public static class Properties {
    @JsonProperty("organizationId")
    private String organizationId;

    @JsonProperty("clusterId")
    private String clusterId;
  }
}

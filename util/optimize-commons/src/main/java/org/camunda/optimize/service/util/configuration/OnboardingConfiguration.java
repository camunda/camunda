/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration;

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

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration;

import com.google.j2objc.annotations.Property;
import lombok.Data;

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
}

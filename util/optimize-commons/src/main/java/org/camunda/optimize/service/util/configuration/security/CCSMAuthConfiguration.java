/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration.security;

import lombok.Data;

@Data
public class CCSMAuthConfiguration {
  // the url to Identity
  private String issuerUrl;
  // the url to Identity (back channel for container to container communication)
  private String issuerBackendUrl;
  // the redirect root url back to Optimize. If not provided, Optimize uses the container url
  private String redirectRootUrl;
  // Identity client id to use by Optimize
  private String clientId;
  // Identity client secret to use by Optimize
  private String clientSecret;
  // Identity audience
  private String audience;
}

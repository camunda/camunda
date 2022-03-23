/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration.security;

import lombok.Data;

@Data
public class CCSMAuthConfiguration {
  // the url to IAM
  private String issuerUrl;
  // IAM client id to use by Optimize
  private String clientId;
  // IAM client secret to use by Optimize
  private String clientSecret;
}

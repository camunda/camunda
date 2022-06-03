/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EnvironmentPropertiesConstants {
  public static final String INTEGRATION_TESTS = "integrationTests";
  public static final String HTTPS_PORT_KEY = "httpsPort";
  public static final String HTTP_PORT_KEY = "httpPort";
}
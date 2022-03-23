/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration;

import org.springframework.context.ApplicationContext;

/**
 * Every class that implements this interfaces is automatically
 * called as soon as {TestEmbeddedCamundaOptimize#reloadConfiguration} is called.
 */
public interface ConfigurationReloadable {

  /**
   * This method reloads the configuration for the implementing class.
   * @param context Application context of the embedded optimize.
   */
  void reloadConfiguration(ApplicationContext context);
}

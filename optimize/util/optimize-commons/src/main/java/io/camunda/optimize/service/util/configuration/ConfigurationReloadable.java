/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import org.springframework.context.ApplicationContext;

/**
 * Every class that implements this interfaces is automatically called as soon as
 * {EmbeddedOptimizeExtension#reloadConfiguration} is called.
 */
public interface ConfigurationReloadable {

  /**
   * This method reloads the configuration for the implementing class.
   *
   * @param context Application context of the embedded optimize.
   */
  void reloadConfiguration(ApplicationContext context);
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import io.camunda.optimize.service.util.ImportJobExecutor;
import io.camunda.optimize.service.util.configuration.ConfigurationService;

public class DatabaseImportJobExecutor extends ImportJobExecutor {

  private final ConfigurationService configurationService;

  public DatabaseImportJobExecutor(
      final String name, final ConfigurationService configurationService) {
    super(name);
    this.configurationService = configurationService;
    startExecutingImportJobs();
  }

  @Override
  protected int getExecutorThreadCount() {
    return configurationService.getJobExecutorThreadCount();
  }

  @Override
  protected int getMaxQueueSize() {
    return configurationService.getJobExecutorQueueSize();
  }
}

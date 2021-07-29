/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es;

import org.camunda.optimize.service.util.ImportJobExecutor;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

public class ElasticsearchImportJobExecutor extends ImportJobExecutor {

  private final ConfigurationService configurationService;

  public ElasticsearchImportJobExecutor(final String name, final ConfigurationService configurationService) {
    super(name);
    this.configurationService = configurationService;
    startExecutingImportJobs();
  }

  @Override
  protected int getExecutorThreadCount() {
    return configurationService.getElasticsearchJobExecutorThreadCount();
  }

  @Override
  protected int getMaxQueueSize() {
    return configurationService.getElasticsearchJobExecutorQueueSize();
  }
}

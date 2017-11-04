package org.camunda.optimize.service.es;

import org.camunda.optimize.service.util.ImportJobExecutor;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ElasticsearchImportJobExecutor extends ImportJobExecutor {

  @Autowired
  private ConfigurationService configurationService;

  @Override
  protected int getExecutorThreadCount() {
    return configurationService.getElasticsearchJobExecutorThreadCount();
  }

  @Override
  protected int getMaxQueueSize() {
    return configurationService.getElasticsearchJobExecutorQueueSize();
  }
}

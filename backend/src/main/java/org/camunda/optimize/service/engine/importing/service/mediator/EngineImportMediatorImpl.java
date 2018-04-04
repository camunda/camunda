package org.camunda.optimize.service.engine.importing.service.mediator;

import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandlerProvider;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.util.BeanHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.client.Client;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class EngineImportMediatorImpl<T extends ImportIndexHandler> implements EngineImportMediator {

  protected T importIndexHandler;
  @Autowired
  protected BeanHelper beanHelper;

  @Autowired
  protected ConfigurationService configurationService;

  @Autowired
  protected Client esClient;

  @Autowired
  protected ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;

  @Autowired
  protected ImportIndexHandlerProvider provider;
}

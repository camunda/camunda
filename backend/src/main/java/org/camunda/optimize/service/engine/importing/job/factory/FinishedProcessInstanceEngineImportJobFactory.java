package org.camunda.optimize.service.engine.importing.job.factory;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.service.engine.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.engine.importing.fetcher.instance.FinishedProcessInstanceFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandlerProvider;
import org.camunda.optimize.service.engine.importing.index.handler.impl.FinishedProcessInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.DefinitionBasedImportPage;
import org.camunda.optimize.service.engine.importing.job.FinishedProcessInstanceEngineImportJob;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.FinishedProcessInstanceWriter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Optional;

@Component
public class FinishedProcessInstanceEngineImportJobFactory implements EngineImportJobFactory {

  private ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private FinishedProcessInstanceImportIndexHandler importIndexHandler;
  private MissingEntitiesFinder<HistoricProcessInstanceDto> missingEntitiesFinder;

  @Autowired
  private FinishedProcessInstanceFetcher engineEntityFetcher;

  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ApplicationContext applicationContext;

  @Autowired
  private org.elasticsearch.client.Client esClient;

  @Autowired
  private FinishedProcessInstanceWriter finishedProcessInstanceWriter;

  @Autowired
  private ImportIndexHandlerProvider provider;

  @PostConstruct
  public void init() {
    importIndexHandler = provider.getFinishedProcessInstanceImportIndexHandler();
    applicationContext.getAutowireCapableBeanFactory().autowireBean(importIndexHandler);
    missingEntitiesFinder = new MissingEntitiesFinder<>(configurationService, esClient, configurationService.getProcessInstanceIdTrackingType());
  }

  @Override
  public long getBackoffTimeInMs() {
    return importIndexHandler.getBackoffTimeInMs();
  }

  public Optional<Runnable> getNextJob() {
    Optional<DefinitionBasedImportPage> page = importIndexHandler.getNextPage();
    return page.map(
      definitionBasedImportPage -> new FinishedProcessInstanceEngineImportJob(
        finishedProcessInstanceWriter,
        definitionBasedImportPage,
        elasticsearchImportJobExecutor,
        missingEntitiesFinder,
        engineEntityFetcher)
    );
  }

  @Override
  public void setElasticsearchImportExecutor(ElasticsearchImportJobExecutor elasticsearchImportJobExecutor) {
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
  }

  public String getElasticsearchType() {
    return configurationService.getProcessInstanceType();
  }


}

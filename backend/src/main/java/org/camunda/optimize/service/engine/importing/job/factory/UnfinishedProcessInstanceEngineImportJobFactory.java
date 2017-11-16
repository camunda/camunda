package org.camunda.optimize.service.engine.importing.job.factory;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.service.engine.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.engine.importing.fetcher.instance.UnfinishedProcessInstanceFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandlerProvider;
import org.camunda.optimize.service.engine.importing.index.handler.impl.UnfinishedProcessInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.IdSetBasedImportPage;
import org.camunda.optimize.service.engine.importing.job.UnfinishedProcessInstanceEngineImportJob;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.UnfinishedProcessInstanceWriter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Optional;

@Component
public class UnfinishedProcessInstanceEngineImportJobFactory implements EngineImportJobFactory {

  private ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private UnfinishedProcessInstanceImportIndexHandler importIndexHandler;
  private MissingEntitiesFinder<HistoricProcessInstanceDto> missingEntitiesFinder;

  @Autowired
  private UnfinishedProcessInstanceFetcher engineEntityFetcher;

  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ApplicationContext applicationContext;

  @Autowired
  private org.elasticsearch.client.Client esClient;

  @Autowired
  private UnfinishedProcessInstanceWriter unfinishedProcessInstanceWriter;

  @Autowired
  private ImportIndexHandlerProvider provider;

  @PostConstruct
  public void init() {
    importIndexHandler = provider.getUnfinishedProcessInstanceImportIndexHandler();
    applicationContext.getAutowireCapableBeanFactory().autowireBean(importIndexHandler);
    missingEntitiesFinder =
      new MissingEntitiesFinder<>(configurationService, esClient, configurationService.getProcessInstanceIdTrackingType());
  }

  @Override
  public void setElasticsearchImportExecutor(ElasticsearchImportJobExecutor elasticsearchImportJobExecutor) {
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
  }

  @Override
  public long getBackoffTimeInMs() {
    return importIndexHandler.getBackoffTimeInMs();
  }

  public Optional<Runnable> getNextJob() {
    Optional<IdSetBasedImportPage> page = importIndexHandler.getNextPage();
    return page.map(
      idSetBasedImportPage -> new UnfinishedProcessInstanceEngineImportJob(
        unfinishedProcessInstanceWriter,
        idSetBasedImportPage,
        elasticsearchImportJobExecutor,
        missingEntitiesFinder,
        engineEntityFetcher)
    );
  }

  public String getElasticsearchType() {
    return configurationService.getProcessInstanceType();
  }


}

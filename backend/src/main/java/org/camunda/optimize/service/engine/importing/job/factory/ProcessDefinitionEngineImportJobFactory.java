package org.camunda.optimize.service.engine.importing.job.factory;

import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.service.engine.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.engine.importing.fetcher.instance.ProcessDefinitionFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandlerProvider;
import org.camunda.optimize.service.engine.importing.index.handler.impl.ProcessDefinitionImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.AllEntitiesBasedImportPage;
import org.camunda.optimize.service.engine.importing.job.ProcessDefinitionEngineImportJob;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Optional;

@Component
public class ProcessDefinitionEngineImportJobFactory implements EngineImportJobFactory {

  private ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private ProcessDefinitionImportIndexHandler importIndexHandler;
  private MissingEntitiesFinder<ProcessDefinitionEngineDto> missingEntitiesFinder;

  @Autowired
  private ProcessDefinitionFetcher engineEntityFetcher;

  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ApplicationContext applicationContext;

  @Autowired
  private org.elasticsearch.client.Client esClient;

  @Autowired
  private ProcessDefinitionWriter processDefinitionWriter;

  @Autowired
  private ImportIndexHandlerProvider provider;

  @PostConstruct
  public void init() {
    importIndexHandler = provider.getProcessDefinitionImportIndexHandler();
    applicationContext.getAutowireCapableBeanFactory().autowireBean(importIndexHandler);
    missingEntitiesFinder = new MissingEntitiesFinder<>(configurationService, esClient, getElasticsearchType());
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
    Optional<AllEntitiesBasedImportPage> page = importIndexHandler.getNextPage();
    return page.map(
      definitionBasedImportPage -> new ProcessDefinitionEngineImportJob(
        processDefinitionWriter,
        definitionBasedImportPage,
        elasticsearchImportJobExecutor,
        missingEntitiesFinder,
        engineEntityFetcher)
    );
  }

  public String getElasticsearchType() {
    return configurationService.getProcessDefinitionType();
  }


}

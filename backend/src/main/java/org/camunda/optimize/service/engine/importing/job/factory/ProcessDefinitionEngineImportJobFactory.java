package org.camunda.optimize.service.engine.importing.job.factory;

import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.service.engine.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.engine.importing.fetcher.instance.ProcessDefinitionFetcher;
import org.camunda.optimize.service.engine.importing.fetcher.instance.ProcessDefinitionXmlFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandlerProvider;
import org.camunda.optimize.service.engine.importing.index.handler.impl.ProcessDefinitionImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.AllEntitiesBasedImportPage;
import org.camunda.optimize.service.engine.importing.job.ProcessDefinitionEngineImportJob;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.util.EngineInstanceHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.client.Client;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Optional;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDefinitionEngineImportJobFactory implements EngineImportJobFactory {

  private ProcessDefinitionImportIndexHandler importIndexHandler;
  private MissingEntitiesFinder<ProcessDefinitionEngineDto> missingEntitiesFinder;
  private ProcessDefinitionFetcher engineEntityFetcher;

  @Autowired
  private ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;

  @Autowired
  private EngineInstanceHelper engineInstanceHelper;

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private Client esClient;

  @Autowired
  private ProcessDefinitionWriter processDefinitionWriter;

  @Autowired
  private ImportIndexHandlerProvider provider;

  protected String engineAlias;

  public ProcessDefinitionEngineImportJobFactory(String engineAlias) {
    this.engineAlias = engineAlias;
  }

  @PostConstruct
  public void init() {
    importIndexHandler = provider.getProcessDefinitionImportIndexHandler(engineAlias);
    engineEntityFetcher = engineInstanceHelper.getInstance(ProcessDefinitionFetcher.class, engineAlias);
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

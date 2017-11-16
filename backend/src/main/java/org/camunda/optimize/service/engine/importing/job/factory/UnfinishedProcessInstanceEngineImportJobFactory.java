package org.camunda.optimize.service.engine.importing.job.factory;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.service.engine.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.engine.importing.fetcher.instance.UnfinishedProcessInstanceFetcher;
import org.camunda.optimize.service.engine.importing.fetcher.instance.VariableInstanceFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandlerProvider;
import org.camunda.optimize.service.engine.importing.index.handler.impl.UnfinishedProcessInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.IdSetBasedImportPage;
import org.camunda.optimize.service.engine.importing.job.UnfinishedProcessInstanceEngineImportJob;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.UnfinishedProcessInstanceWriter;
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
public class UnfinishedProcessInstanceEngineImportJobFactory implements EngineImportJobFactory {

  private UnfinishedProcessInstanceImportIndexHandler importIndexHandler;
  private MissingEntitiesFinder<HistoricProcessInstanceDto> missingEntitiesFinder;
  private UnfinishedProcessInstanceFetcher engineEntityFetcher;

  @Autowired
  private ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;

  @Autowired
  private EngineInstanceHelper engineInstanceHelper;

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private Client esClient;

  @Autowired
  private UnfinishedProcessInstanceWriter unfinishedProcessInstanceWriter;

  @Autowired
  private ImportIndexHandlerProvider provider;

  protected String engineAlias;

  public UnfinishedProcessInstanceEngineImportJobFactory(String engineAlias) {
    this.engineAlias = engineAlias;
  }

  @PostConstruct
  public void init() {
    importIndexHandler = provider.getUnfinishedProcessInstanceImportIndexHandler(engineAlias);
    engineEntityFetcher = engineInstanceHelper.getInstance(UnfinishedProcessInstanceFetcher.class, engineAlias);
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

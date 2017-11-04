package org.camunda.optimize.service.engine.importing.job.factory;

import org.camunda.optimize.dto.engine.HistoricVariableInstanceDto;
import org.camunda.optimize.plugin.ImportAdapterProvider;
import org.camunda.optimize.service.engine.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.engine.importing.fetcher.instance.VariableInstanceFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandlerProvider;
import org.camunda.optimize.service.engine.importing.index.handler.impl.VariableInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.IdSetBasedImportPage;
import org.camunda.optimize.service.engine.importing.job.VariableInstanceEngineImportJob;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.VariableWriter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.ws.rs.client.Client;
import java.util.Optional;

@Component
public class VariableInstanceEngineImportJobFactory implements EngineImportJobFactory {

  private ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private VariableInstanceImportIndexHandler importIndexHandler;
  private MissingEntitiesFinder<HistoricVariableInstanceDto> missingEntitiesFinder;

  @Autowired
  private VariableInstanceFetcher engineEntityFetcher;


  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ApplicationContext applicationContext;

  @Autowired
  private Client engineClient;

  @Autowired
  private org.elasticsearch.client.Client esClient;

  @Autowired
  private VariableWriter variableWriter;

  @Autowired
  private ImportAdapterProvider importAdapterProvider;

  @Autowired
  private ImportIndexHandlerProvider provider;

  @PostConstruct
  public void init(){
    importIndexHandler = provider.getVariableInstanceImportIndexHandler();
    missingEntitiesFinder = new MissingEntitiesFinder<>(configurationService, esClient, getElasticsearchType());
  }

  @Override
  public long getBackoffTimeInMs() {
    return importIndexHandler.getBackoffTimeInMs();
  }

  @Override
  public void setElasticsearchImportExecutor(ElasticsearchImportJobExecutor elasticsearchImportJobExecutor) {
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
  }

  public Optional<Runnable> getNextJob() {
    Optional<IdSetBasedImportPage> page = importIndexHandler.getNextPage();
    return page.map(
      idSetBasedImportPage -> new VariableInstanceEngineImportJob(
        variableWriter,
        importAdapterProvider,
        idSetBasedImportPage,
        elasticsearchImportJobExecutor,
        missingEntitiesFinder,
        engineEntityFetcher)
    );
  }

  public String getElasticsearchType() {
    return configurationService.getVariableType();
  }


}

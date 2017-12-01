package org.camunda.optimize.service.engine.importing.job.factory;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.engine.importing.fetcher.instance.FinishedProcessInstanceFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandlerProvider;
import org.camunda.optimize.service.engine.importing.index.handler.impl.FinishedProcessInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.DefinitionBasedImportPage;
import org.camunda.optimize.service.engine.importing.job.FinishedProcessInstanceEngineImportJob;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.FinishedProcessInstanceWriter;
import org.camunda.optimize.service.util.BeanHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.client.Client;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Optional;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class FinishedProcessInstanceEngineImportJobFactory implements EngineImportJobFactory {

  private FinishedProcessInstanceImportIndexHandler importIndexHandler;
  private MissingEntitiesFinder<HistoricProcessInstanceDto> missingEntitiesFinder;
  private FinishedProcessInstanceFetcher engineEntityFetcher;

  @Autowired
  private ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;

  @Autowired
  private BeanHelper beanHelper;

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private Client esClient;

  @Autowired
  private FinishedProcessInstanceWriter finishedProcessInstanceWriter;

  @Autowired
  private ImportIndexHandlerProvider provider;

  protected EngineContext engineContext;

  public FinishedProcessInstanceEngineImportJobFactory(EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @PostConstruct
  public void init() {
    importIndexHandler = provider.getFinishedProcessInstanceImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanHelper.getInstance(FinishedProcessInstanceFetcher.class, engineContext);
    missingEntitiesFinder = new MissingEntitiesFinder<>(
        configurationService,
        esClient,
        configurationService.getFinishedProcessInstanceIdTrackingType()
    );
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
        engineEntityFetcher,
        engineContext
      )
    );
  }


}

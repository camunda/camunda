package org.camunda.optimize.service.engine.importing.job.factory;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.engine.importing.fetcher.instance.UnfinishedProcessInstanceFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandlerProvider;
import org.camunda.optimize.service.engine.importing.index.handler.impl.UnfinishedProcessInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.IdSetBasedImportPage;
import org.camunda.optimize.service.engine.importing.job.UnfinishedProcessInstanceEngineImportJob;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.UnfinishedProcessInstanceWriter;
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
public class UnfinishedProcessInstanceEngineImportJobFactory
    extends EngineImportJobFactoryImpl<UnfinishedProcessInstanceImportIndexHandler>
    implements EngineImportJobFactory {

  private MissingEntitiesFinder<HistoricProcessInstanceDto> missingEntitiesFinder;
  private UnfinishedProcessInstanceFetcher engineEntityFetcher;

  @Autowired
  private UnfinishedProcessInstanceWriter unfinishedProcessInstanceWriter;


  protected EngineContext engineContext;

  public UnfinishedProcessInstanceEngineImportJobFactory(EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @PostConstruct
  public void init() {
    importIndexHandler = provider.getUnfinishedProcessInstanceImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanHelper.getInstance(UnfinishedProcessInstanceFetcher.class, engineContext);
    missingEntitiesFinder = new MissingEntitiesFinder<>(
        configurationService,
        esClient,
        configurationService.getUnfinishedProcessInstanceIdTrackingType()
    );
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
        engineEntityFetcher,
        engineContext
      )
    );
  }

}

package org.camunda.optimize.service.engine.importing.job.factory;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.engine.importing.fetcher.instance.ActivityInstanceFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandlerProvider;
import org.camunda.optimize.service.engine.importing.index.handler.impl.ActivityImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.DefinitionBasedImportPage;
import org.camunda.optimize.service.engine.importing.job.ActivityInstanceEngineImportJob;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.EventsWriter;
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
public class ActivityInstanceEngineImportJobFactory
    extends EngineImportJobFactoryImpl<ActivityImportIndexHandler>
    implements EngineImportJobFactory {

  private MissingEntitiesFinder<HistoricActivityInstanceEngineDto> missingActivityFinder;
  private ActivityInstanceFetcher engineEntityFetcher;


  @Autowired
  private EventsWriter eventsWriter;

  protected EngineContext engineContext;


  public ActivityInstanceEngineImportJobFactory (EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @Override
  public long getBackoffTimeInMs() {
    return importIndexHandler.getBackoffTimeInMs();
  }

  @PostConstruct
  public void init() {
    importIndexHandler = provider.getActivityImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanHelper.getInstance(ActivityInstanceFetcher.class, engineContext);
    missingActivityFinder = new MissingEntitiesFinder<>(
        configurationService,
        esClient,
        configurationService.getEventType()
    );
  }

  public Optional<Runnable> getNextJob() {
    Optional<DefinitionBasedImportPage> page = importIndexHandler.getNextPage();
    return page.map(
      definitionBasedImportPage -> new ActivityInstanceEngineImportJob(
        eventsWriter,
        definitionBasedImportPage,
        elasticsearchImportJobExecutor,
        missingActivityFinder,
        engineEntityFetcher,
        engineContext
      )
    );
  }
}

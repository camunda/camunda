package org.camunda.optimize.service.engine.importing.job.factory;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
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
public class ActivityInstanceEngineImportJobFactory implements EngineImportJobFactory {

  private ActivityImportIndexHandler activityImportIndexHandler;
  private MissingEntitiesFinder<HistoricActivityInstanceEngineDto> missingActivityFinder;
  private ActivityInstanceFetcher engineEntityFetcher;

  @Autowired
  private ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;

  @Autowired
  private BeanHelper beanHelper;

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private Client esClient;

  @Autowired
  private EventsWriter eventsWriter;

  @Autowired
  private ImportIndexHandlerProvider provider;

  protected String engineAlias;


  public ActivityInstanceEngineImportJobFactory (String engineAlias) {
    this.engineAlias = engineAlias;
  }

  @Override
  public long getBackoffTimeInMs() {
    return activityImportIndexHandler.getBackoffTimeInMs();
  }

  @PostConstruct
  public void init() {
    activityImportIndexHandler = provider.getActivityImportIndexHandler(engineAlias);
    engineEntityFetcher = beanHelper.getInstance(ActivityInstanceFetcher.class, engineAlias);
    missingActivityFinder = new MissingEntitiesFinder<>(
        configurationService,
        esClient,
        configurationService.getEventType()
    );
  }

  public Optional<Runnable> getNextJob() {
    Optional<DefinitionBasedImportPage> page = activityImportIndexHandler.getNextPage();
    return page.map(
      definitionBasedImportPage -> new ActivityInstanceEngineImportJob(
        eventsWriter,
        definitionBasedImportPage,
        elasticsearchImportJobExecutor,
        missingActivityFinder,
        engineEntityFetcher,
        engineAlias
      )
    );
  }
}

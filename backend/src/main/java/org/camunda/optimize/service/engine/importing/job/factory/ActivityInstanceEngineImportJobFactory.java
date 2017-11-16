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
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Optional;

@Component
public class ActivityInstanceEngineImportJobFactory implements EngineImportJobFactory {

  private ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private ActivityImportIndexHandler activityImportIndexHandler;
  private MissingEntitiesFinder<HistoricActivityInstanceEngineDto> missingActivityFinder;

  @Autowired
  private ActivityInstanceFetcher engineEntityFetcher;

  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ApplicationContext applicationContext;

  @Autowired
  private org.elasticsearch.client.Client esClient;

  @Autowired
  private EventsWriter eventsWriter;

  @Autowired
  private ImportIndexHandlerProvider provider;

  @Override
  public void setElasticsearchImportExecutor(ElasticsearchImportJobExecutor elasticsearchImportJobExecutor) {
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
  }

  @Override
  public long getBackoffTimeInMs() {
    return activityImportIndexHandler.getBackoffTimeInMs();
  }

  @PostConstruct
  public void init() {
    activityImportIndexHandler = provider.getActivityImportIndexHandler();
    applicationContext.getAutowireCapableBeanFactory().autowireBean(activityImportIndexHandler);
    missingActivityFinder = new MissingEntitiesFinder<>(configurationService, esClient, getElasticsearchType());
  }

  public Optional<Runnable> getNextJob() {
    Optional<DefinitionBasedImportPage> page = activityImportIndexHandler.getNextPage();
    return page.map(
      definitionBasedImportPage -> new ActivityInstanceEngineImportJob(
        eventsWriter,
        definitionBasedImportPage,
        elasticsearchImportJobExecutor,
        missingActivityFinder,
        engineEntityFetcher)
    );
  }

  public String getElasticsearchType() {
    return configurationService.getEventType();
  }


}

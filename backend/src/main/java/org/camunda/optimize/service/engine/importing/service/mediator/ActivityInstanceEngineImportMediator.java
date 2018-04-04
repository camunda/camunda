package org.camunda.optimize.service.engine.importing.service.mediator;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.engine.importing.fetcher.instance.ActivityInstanceFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.impl.ActivityImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.DefinitionBasedImportPage;
import org.camunda.optimize.service.engine.importing.service.ActivityInstanceImportService;
import org.camunda.optimize.service.es.writer.EventsWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Optional;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ActivityInstanceEngineImportMediator
    extends EngineImportMediatorImpl<ActivityImportIndexHandler> {

  private MissingEntitiesFinder<HistoricActivityInstanceEngineDto> missingActivityFinder;
  private ActivityInstanceFetcher engineEntityFetcher;


  @Autowired
  private EventsWriter eventsWriter;

  protected EngineContext engineContext;

  private ActivityInstanceImportService activityInstanceImportService;


  public ActivityInstanceEngineImportMediator(EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @Override
  public long getBackoffTimeInMs() {
    return importIndexHandler.getBackoffTimeInMs();
  }

  @Override
  public boolean canImport() {
    return importIndexHandler.hasNewPage();
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
    activityInstanceImportService = new ActivityInstanceImportService(
        eventsWriter,
        elasticsearchImportJobExecutor,
        missingActivityFinder,
        engineEntityFetcher,
        engineContext
      );
  }

  @Override
  public void importNextPage() {
    Optional<DefinitionBasedImportPage> page = importIndexHandler.getNextPage();
    page.ifPresent(activityInstanceImportService::executeImport);
  }
}

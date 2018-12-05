package org.camunda.optimize.service.engine.importing.service.mediator;

import org.apache.commons.collections.ListUtils;
import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.fetcher.instance.RunningActivityInstanceFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.impl.RunningActivityInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.TimestampBasedImportPage;
import org.camunda.optimize.service.engine.importing.service.RunningActivityInstanceImportService;
import org.camunda.optimize.service.es.writer.RunningActivityInstanceWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class RunningActivityInstanceEngineImportMediator
  extends BackoffImportMediator<RunningActivityInstanceImportIndexHandler> {

  private RunningActivityInstanceFetcher engineEntityFetcher;

  @Autowired
  private RunningActivityInstanceWriter runningActivityInstanceWriter;

  private RunningActivityInstanceImportService runningActivityInstanceImportService;


  public RunningActivityInstanceEngineImportMediator(EngineContext engineContext) {
    super(engineContext);
  }

  @PostConstruct
  public void init() {
    importIndexHandler = provider.getRunningActivityInstanceImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanHelper.getInstance(RunningActivityInstanceFetcher.class, engineContext);
    runningActivityInstanceImportService = new RunningActivityInstanceImportService(
      runningActivityInstanceWriter, elasticsearchImportJobExecutor, engineContext
    );
  }

  @Override
  public boolean importNextEnginePage() {
    final List<HistoricActivityInstanceEngineDto> entitiesOfLastTimestamp = engineEntityFetcher
      .fetchRunningActivityInstancesForTimestamp(importIndexHandler.getTimestampOfLastEntity());

    final TimestampBasedImportPage page = importIndexHandler.getNextPage();
    final List<HistoricActivityInstanceEngineDto> nextPageEntities = engineEntityFetcher
      .fetchRunningActivityInstances(page);

    if (!nextPageEntities.isEmpty()) {
      OffsetDateTime timestamp = nextPageEntities.get(nextPageEntities.size() - 1).getStartTime();
      importIndexHandler.updateTimestampOfLastEntity(timestamp);
    }

    if (!entitiesOfLastTimestamp.isEmpty() || !nextPageEntities.isEmpty()) {
      final List<HistoricActivityInstanceEngineDto> allEntities =
        ListUtils.union(entitiesOfLastTimestamp, nextPageEntities);
      runningActivityInstanceImportService.executeImport(allEntities);
    }

    return nextPageEntities.size() >= configurationService.getEngineImportActivityInstanceMaxPageSize();
  }
}

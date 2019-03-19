package org.camunda.optimize.service.engine.importing.service.mediator;

import org.apache.commons.collections.ListUtils;
import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.fetcher.instance.RunningProcessInstanceFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.impl.RunningProcessInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.TimestampBasedImportPage;
import org.camunda.optimize.service.engine.importing.service.RunningProcessInstanceImportService;
import org.camunda.optimize.service.es.writer.RunningProcessInstanceWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class RunningProcessInstanceEngineImportMediator
  extends BackoffImportMediator<RunningProcessInstanceImportIndexHandler> {

  private RunningProcessInstanceFetcher engineEntityFetcher;
  private RunningProcessInstanceImportService runningProcessInstanceImportService;
  @Autowired
  private RunningProcessInstanceWriter runningProcessInstanceWriter;

  public RunningProcessInstanceEngineImportMediator(EngineContext engineContext) {
    super(engineContext);
  }

  @PostConstruct
  public void init() {
    importIndexHandler = provider.getRunningProcessInstanceImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanFactory.getBean(RunningProcessInstanceFetcher.class, engineContext);
    runningProcessInstanceImportService = new RunningProcessInstanceImportService(
      runningProcessInstanceWriter, elasticsearchImportJobExecutor, engineContext
    );
  }

  @Override
  protected boolean importNextEnginePage() {
    final List<HistoricProcessInstanceDto> entitiesOfLastTimestamp = engineEntityFetcher
      .fetchRunningProcessInstances(importIndexHandler.getTimestampOfLastEntity());

    final TimestampBasedImportPage page = importIndexHandler.getNextPage();
    final List<HistoricProcessInstanceDto> nextPageEntities = engineEntityFetcher
      .fetchRunningProcessInstances(page);

    boolean timestampNeedsToBeSet = !nextPageEntities.isEmpty();

    OffsetDateTime timestamp = timestampNeedsToBeSet ?
      nextPageEntities.get(nextPageEntities.size() - 1).getStartTime() :
      null;

    if (timestampNeedsToBeSet) {
      importIndexHandler.updatePendingTimestampOfLastEntity(timestamp);
    }

    if (!entitiesOfLastTimestamp.isEmpty() || timestampNeedsToBeSet) {
      final List<HistoricProcessInstanceDto> allEntities = ListUtils.union(entitiesOfLastTimestamp, nextPageEntities);
      runningProcessInstanceImportService.executeImport(allEntities, () -> {
        if (timestampNeedsToBeSet) {
          importIndexHandler.updateTimestampOfLastEntity(timestamp);
        }
      });
    }

    return nextPageEntities.size() >= configurationService.getEngineImportProcessInstanceMaxPageSize();
  }

}

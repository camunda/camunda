package org.camunda.optimize.service.engine.importing.service.mediator;

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

  protected EngineContext engineContext;
  private RunningProcessInstanceFetcher engineEntityFetcher;
  private RunningProcessInstanceImportService runningProcessInstanceImportService;
  @Autowired
  private RunningProcessInstanceWriter runningProcessInstanceWriter;

  public RunningProcessInstanceEngineImportMediator(EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @PostConstruct
  public void init() {
    importIndexHandler = provider.getUnfinishedProcessInstanceImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanHelper.getInstance(RunningProcessInstanceFetcher.class, engineContext);
    runningProcessInstanceImportService = new RunningProcessInstanceImportService(
      runningProcessInstanceWriter,
      elasticsearchImportJobExecutor,
      engineContext
    );
  }

  @Override
  protected boolean importNextEnginePage() {
    List<HistoricProcessInstanceDto> entitiesOfLastTimestamp =
      engineEntityFetcher.fetchHistoricFinishedProcessInstances(importIndexHandler.getTimestampOfLastEntity());

    TimestampBasedImportPage page = importIndexHandler.getNextPage();
    List<HistoricProcessInstanceDto> entities =
      engineEntityFetcher.fetchHistoricFinishedProcessInstances(page);
    if (!entities.isEmpty()) {

      OffsetDateTime timestamp = entities.get(entities.size() - 1).getStartTime();
      importIndexHandler.updateTimestampOfLastEntity(timestamp);
      entities.addAll(entitiesOfLastTimestamp);
      runningProcessInstanceImportService.executeImport(entities);
    }
    return entities.size() >= configurationService.getEngineImportProcessInstanceMaxPageSize();
  }

}

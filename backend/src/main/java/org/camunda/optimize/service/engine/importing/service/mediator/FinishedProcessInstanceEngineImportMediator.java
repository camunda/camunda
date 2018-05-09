package org.camunda.optimize.service.engine.importing.service.mediator;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.fetcher.instance.FinishedProcessInstanceFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.impl.FinishedProcessInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.TimestampBasedImportPage;
import org.camunda.optimize.service.engine.importing.service.FinishedProcessInstanceImportService;
import org.camunda.optimize.service.es.writer.FinishedProcessInstanceWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class FinishedProcessInstanceEngineImportMediator
  extends BackoffImportMediator<FinishedProcessInstanceImportIndexHandler> {

  private FinishedProcessInstanceFetcher engineEntityFetcher;
  private FinishedProcessInstanceImportService finishedProcessInstanceImportService;
  @Autowired
  private FinishedProcessInstanceWriter finishedProcessInstanceWriter;

  public FinishedProcessInstanceEngineImportMediator(EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @PostConstruct
  public void init() {
    importIndexHandler = provider.getFinishedProcessInstanceImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanHelper.getInstance(FinishedProcessInstanceFetcher.class, engineContext);
    finishedProcessInstanceImportService = new FinishedProcessInstanceImportService(
      finishedProcessInstanceWriter,
      elasticsearchImportJobExecutor,
      engineContext
    );
  }

  @Override
  public boolean importNextEnginePage() {
    List<HistoricProcessInstanceDto> entitiesOfLastTimestamp =
      engineEntityFetcher.fetchHistoricFinishedProcessInstances(importIndexHandler.getTimestampOfLastEntity());

    TimestampBasedImportPage page = importIndexHandler.getNextPage();
    List<HistoricProcessInstanceDto> entities =
      engineEntityFetcher.fetchHistoricFinishedProcessInstances(page);
    if (!entities.isEmpty()) {

      OffsetDateTime timestamp = entities.get(entities.size() - 1).getEndTime();
      importIndexHandler.updateTimestampOfLastEntity(timestamp);
      entities.addAll(entitiesOfLastTimestamp);
      finishedProcessInstanceImportService.executeImport(entities);
    }
    return entities.size() >= configurationService.getEngineImportProcessInstanceMaxPageSize();
  }

}

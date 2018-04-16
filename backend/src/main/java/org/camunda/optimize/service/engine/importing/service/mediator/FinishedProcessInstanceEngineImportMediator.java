package org.camunda.optimize.service.engine.importing.service.mediator;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.engine.importing.fetcher.instance.FinishedProcessInstanceFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.impl.FinishedProcessInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.DefinitionBasedImportPage;
import org.camunda.optimize.service.engine.importing.service.FinishedProcessInstanceImportService;
import org.camunda.optimize.service.es.writer.FinishedProcessInstanceWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
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
    MissingEntitiesFinder<HistoricProcessInstanceDto> missingEntitiesFinder =
      new MissingEntitiesFinder<>(
        configurationService,
        esClient,
        configurationService.getFinishedProcessInstanceIdTrackingType()
      );
    finishedProcessInstanceImportService = new FinishedProcessInstanceImportService(
      finishedProcessInstanceWriter,
      elasticsearchImportJobExecutor,
      missingEntitiesFinder,
      engineContext
    );
  }

  @Override
  public boolean importNextEnginePage() {
    DefinitionBasedImportPage page = importIndexHandler.getNextPage();
    List<HistoricProcessInstanceDto> entities = engineEntityFetcher.fetchEngineEntities(page);
    if (!entities.isEmpty()) {
      // we have to subtract one millisecond because the operator for comparing (finished after) timestamps
      // in the engine is >= . Therefore we add the small count of the smallest unit to achieve the > operator
      OffsetDateTime timestamp = entities.get(entities.size() - 1).getEndTime().plus(1L, ChronoUnit.MILLIS);
      importIndexHandler.updateIndexTimestamp(timestamp);
      finishedProcessInstanceImportService.executeImport(entities);
    }
    if (entities.size() < configurationService.getEngineImportProcessInstanceMaxPageSize()) {
      if (importIndexHandler.finishedDefinitionRoundWithoutNewData()) {
        return false;
      } else {
        importIndexHandler.moveToNextDefinitionToImport();
      }
    }
    return true;
  }

}

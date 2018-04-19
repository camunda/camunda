package org.camunda.optimize.service.engine.importing.service.mediator;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.engine.importing.fetcher.instance.RunningProcessInstanceFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.impl.RunningProcessInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.IdSetBasedImportPage;
import org.camunda.optimize.service.engine.importing.service.RunningProcessInstanceImportService;
import org.camunda.optimize.service.es.writer.RunningProcessInstanceWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
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
    MissingEntitiesFinder<HistoricProcessInstanceDto> missingEntitiesFinder =
      new MissingEntitiesFinder<>(
        configurationService,
        esClient,
        configurationService.getUnfinishedProcessInstanceIdTrackingType()
      );
    runningProcessInstanceImportService = new RunningProcessInstanceImportService(
      runningProcessInstanceWriter,
      elasticsearchImportJobExecutor,
      missingEntitiesFinder,
      engineContext
    );
  }

  @Override
  protected boolean importNextEnginePage() {
    IdSetBasedImportPage page = importIndexHandler.getNextPage();
    if (!page.getIds().isEmpty()) {
      List<HistoricProcessInstanceDto> entities = engineEntityFetcher.fetchEngineEntities(page);
      if (!entities.isEmpty()) {
        runningProcessInstanceImportService.executeImport(entities);
        return true;
      }
    }
    return false;
  }

}

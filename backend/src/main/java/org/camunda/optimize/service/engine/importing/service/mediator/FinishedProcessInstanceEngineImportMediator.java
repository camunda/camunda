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
import java.util.Optional;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class FinishedProcessInstanceEngineImportMediator
    extends EngineImportMediatorImpl<FinishedProcessInstanceImportIndexHandler> {

  private FinishedProcessInstanceImportIndexHandler importIndexHandler;
  private MissingEntitiesFinder<HistoricProcessInstanceDto> missingEntitiesFinder;
  private FinishedProcessInstanceFetcher engineEntityFetcher;

  private FinishedProcessInstanceImportService finishedProcessInstanceImportService;

  @Autowired
  private FinishedProcessInstanceWriter finishedProcessInstanceWriter;

  protected EngineContext engineContext;

  public FinishedProcessInstanceEngineImportMediator(EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @PostConstruct
  public void init() {
    importIndexHandler = provider.getFinishedProcessInstanceImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanHelper.getInstance(FinishedProcessInstanceFetcher.class, engineContext);
    missingEntitiesFinder = new MissingEntitiesFinder<>(
        configurationService,
        esClient,
        configurationService.getFinishedProcessInstanceIdTrackingType()
    );
    finishedProcessInstanceImportService =  new FinishedProcessInstanceImportService(
        finishedProcessInstanceWriter,
        elasticsearchImportJobExecutor,
        missingEntitiesFinder,
        engineEntityFetcher,
        engineContext
      );
  }

  @Override
  public void importNextPage() {
    Optional<DefinitionBasedImportPage> page = importIndexHandler.getNextPage();
    page.ifPresent(finishedProcessInstanceImportService::executeImport);
  }

  @Override
  public boolean canImport() {
    return importIndexHandler.hasNewPage();
  }

  @Override
  public long getBackoffTimeInMs() {
    return importIndexHandler.getBackoffTimeInMs();
  }

}

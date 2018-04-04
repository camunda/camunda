package org.camunda.optimize.service.engine.importing.service.mediator;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.engine.importing.fetcher.instance.UnfinishedProcessInstanceFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.impl.UnfinishedProcessInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.IdSetBasedImportPage;
import org.camunda.optimize.service.engine.importing.service.UnfinishedProcessInstanceImportService;
import org.camunda.optimize.service.es.writer.UnfinishedProcessInstanceWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Optional;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class UnfinishedProcessInstanceEngineImportMediator
    extends EngineImportMediatorImpl<UnfinishedProcessInstanceImportIndexHandler> {

  private MissingEntitiesFinder<HistoricProcessInstanceDto> missingEntitiesFinder;
  private UnfinishedProcessInstanceFetcher engineEntityFetcher;

  private UnfinishedProcessInstanceImportService unfinishedProcessInstanceImportService;

  @Autowired
  private UnfinishedProcessInstanceWriter unfinishedProcessInstanceWriter;


  protected EngineContext engineContext;

  public UnfinishedProcessInstanceEngineImportMediator(EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @PostConstruct
  public void init() {
    importIndexHandler = provider.getUnfinishedProcessInstanceImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanHelper.getInstance(UnfinishedProcessInstanceFetcher.class, engineContext);
    missingEntitiesFinder = new MissingEntitiesFinder<>(
        configurationService,
        esClient,
        configurationService.getUnfinishedProcessInstanceIdTrackingType()
    );
    unfinishedProcessInstanceImportService = new UnfinishedProcessInstanceImportService(
        unfinishedProcessInstanceWriter,
        elasticsearchImportJobExecutor,
        missingEntitiesFinder,
        engineEntityFetcher,
        engineContext
      );
  }

  @Override
  public void importNextPage() {
    Optional<IdSetBasedImportPage> page = importIndexHandler.getNextPage();
    page.ifPresent(unfinishedProcessInstanceImportService::executeImport);
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

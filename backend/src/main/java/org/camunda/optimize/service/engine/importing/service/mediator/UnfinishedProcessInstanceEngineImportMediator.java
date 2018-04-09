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
import java.util.List;
import java.util.Optional;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class UnfinishedProcessInstanceEngineImportMediator
    extends BackoffImportMediator<UnfinishedProcessInstanceImportIndexHandler> {

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
    MissingEntitiesFinder<HistoricProcessInstanceDto> missingEntitiesFinder =
      new MissingEntitiesFinder<>(
        configurationService,
        esClient,
        configurationService.getUnfinishedProcessInstanceIdTrackingType()
      );
    unfinishedProcessInstanceImportService = new UnfinishedProcessInstanceImportService(
      unfinishedProcessInstanceWriter,
      elasticsearchImportJobExecutor,
      missingEntitiesFinder,
      engineContext
    );
  }

  @Override
  protected boolean importNextEnginePage() {
    Optional<IdSetBasedImportPage> page = importIndexHandler.getNextPage();
    if (page.isPresent() && !page.get().getIds().isEmpty()) {
      List<HistoricProcessInstanceDto> entities =  engineEntityFetcher.fetchEngineEntities(page.get());
      if (!entities.isEmpty()) {
        unfinishedProcessInstanceImportService.executeImport(entities);
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean hasNewPage() {
    return importIndexHandler.hasNewPage();
  }

}

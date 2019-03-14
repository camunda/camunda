package org.camunda.optimize.service.engine.importing.service.mediator;

import org.apache.commons.collections.ListUtils;
import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.fetcher.instance.CompletedActivityInstanceFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.impl.CompletedActivityInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.TimestampBasedImportPage;
import org.camunda.optimize.service.engine.importing.service.CompletedActivityInstanceImportService;
import org.camunda.optimize.service.es.writer.CompletedActivityInstanceWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CompletedActivityInstanceEngineImportMediator
  extends BackoffImportMediator<CompletedActivityInstanceImportIndexHandler> {

  private CompletedActivityInstanceFetcher engineEntityFetcher;

  @Autowired
  private CompletedActivityInstanceWriter completedActivityInstanceWriter;

  private CompletedActivityInstanceImportService completedActivityInstanceImportService;


  public CompletedActivityInstanceEngineImportMediator(EngineContext engineContext) {
    super(engineContext);
  }

  @PostConstruct
  public void init() {
    importIndexHandler = provider.getCompletedActivityInstanceImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanHelper.getInstance(CompletedActivityInstanceFetcher.class, engineContext);
    completedActivityInstanceImportService = new CompletedActivityInstanceImportService(
      completedActivityInstanceWriter, elasticsearchImportJobExecutor, engineContext
    );
  }

  @Override
  public boolean importNextEnginePage() {
    final List<HistoricActivityInstanceEngineDto> entitiesOfLastTimestamp = engineEntityFetcher
      .fetchCompletedActivityInstancesForTimestamp(importIndexHandler.getTimestampOfLastEntity());

    final TimestampBasedImportPage page = importIndexHandler.getNextPage();
    final List<HistoricActivityInstanceEngineDto> nextPageEntities = engineEntityFetcher
      .fetchCompletedActivityInstances(page);

    boolean timestampNeedsToBeSet = !nextPageEntities.isEmpty();

    OffsetDateTime timestamp = timestampNeedsToBeSet ?
      nextPageEntities.get(nextPageEntities.size() - 1).getEndTime() :
      null;

    if (timestampNeedsToBeSet) {
      importIndexHandler.updatePendingTimestampOfLastEntity(timestamp);
    }

    if (!entitiesOfLastTimestamp.isEmpty() || timestampNeedsToBeSet) {
      final List<HistoricActivityInstanceEngineDto> allEntities = ListUtils.union(entitiesOfLastTimestamp, nextPageEntities);
      completedActivityInstanceImportService.executeImport(allEntities, () -> {
        if (timestampNeedsToBeSet) importIndexHandler.updateTimestampOfLastEntity(timestamp);
      });
    }

    return nextPageEntities.size() >= configurationService.getEngineImportActivityInstanceMaxPageSize();
  }
}

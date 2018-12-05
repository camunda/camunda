package org.camunda.optimize.service.engine.importing.service.mediator;

import org.apache.commons.collections.ListUtils;
import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.fetcher.instance.CompletedProcessInstanceFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.impl.CompletedProcessInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.TimestampBasedImportPage;
import org.camunda.optimize.service.engine.importing.service.CompletedProcessInstanceImportService;
import org.camunda.optimize.service.es.writer.CompletedProcessInstanceWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CompletedProcessInstanceEngineImportMediator
  extends BackoffImportMediator<CompletedProcessInstanceImportIndexHandler> {

  private CompletedProcessInstanceFetcher engineEntityFetcher;
  private CompletedProcessInstanceImportService completedProcessInstanceImportService;
  @Autowired
  private CompletedProcessInstanceWriter completedProcessInstanceWriter;

  public CompletedProcessInstanceEngineImportMediator(EngineContext engineContext) {
    super(engineContext);
  }

  @PostConstruct
  public void init() {
    importIndexHandler = provider.getCompletedProcessInstanceImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanHelper.getInstance(CompletedProcessInstanceFetcher.class, engineContext);
    completedProcessInstanceImportService = new CompletedProcessInstanceImportService(
      completedProcessInstanceWriter, elasticsearchImportJobExecutor, engineContext
    );
  }

  @Override
  public boolean importNextEnginePage() {
    final List<HistoricProcessInstanceDto> entitiesOfLastTimestamp = engineEntityFetcher
      .fetchCompletedProcessInstances(importIndexHandler.getTimestampOfLastEntity());

    final TimestampBasedImportPage page = importIndexHandler.getNextPage();
    final List<HistoricProcessInstanceDto> nextPageEntities = engineEntityFetcher
      .fetchCompletedProcessInstances(page);

    if (!nextPageEntities.isEmpty()) {
      OffsetDateTime timestamp = nextPageEntities.get(nextPageEntities.size() - 1).getEndTime();
      importIndexHandler.updateTimestampOfLastEntity(timestamp);
    }

    if (!entitiesOfLastTimestamp.isEmpty() || !nextPageEntities.isEmpty()) {
      final List<HistoricProcessInstanceDto> allEntities = ListUtils.union(entitiesOfLastTimestamp, nextPageEntities);
      completedProcessInstanceImportService.executeImport(allEntities);
    }

    return nextPageEntities.size() >= configurationService.getEngineImportProcessInstanceMaxPageSize();
  }

}

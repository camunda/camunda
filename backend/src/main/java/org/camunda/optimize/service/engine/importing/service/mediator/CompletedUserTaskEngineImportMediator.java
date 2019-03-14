package org.camunda.optimize.service.engine.importing.service.mediator;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.dto.engine.HistoricUserTaskInstanceDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.fetcher.instance.CompletedUserTaskInstanceFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.impl.CompletedUserTaskInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.TimestampBasedImportPage;
import org.camunda.optimize.service.engine.importing.service.CompletedUserTaskInstanceImportService;
import org.camunda.optimize.service.es.writer.CompletedUserTaskInstanceWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CompletedUserTaskEngineImportMediator
  extends BackoffImportMediator<CompletedUserTaskInstanceImportIndexHandler> {

  private CompletedUserTaskInstanceFetcher engineEntityFetcher;

  @Autowired
  private CompletedUserTaskInstanceWriter completedUserTaskInstanceWriter;

  private CompletedUserTaskInstanceImportService completedUserTaskInstanceImportService;

  public CompletedUserTaskEngineImportMediator(EngineContext engineContext) {
    super(engineContext);
  }

  @PostConstruct
  public void init() {
    importIndexHandler = provider.getCompletedUserTaskInstanceImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanHelper.getInstance(CompletedUserTaskInstanceFetcher.class, engineContext);
    completedUserTaskInstanceImportService = new CompletedUserTaskInstanceImportService(
      completedUserTaskInstanceWriter,
      elasticsearchImportJobExecutor,
      engineContext
    );
  }

  @Override
  public boolean importNextEnginePage() {
    final List<HistoricUserTaskInstanceDto> userTaskEntitiesOfLastTimestamp =
      engineEntityFetcher.fetchCompletedUserTaskInstancesForTimestamp(importIndexHandler.getTimestampOfLastEntity());

    final TimestampBasedImportPage page = importIndexHandler.getNextPage();
    final List<HistoricUserTaskInstanceDto> nextPageUserTaskEntities = engineEntityFetcher
      .fetchCompletedUserTaskInstances(page);

    boolean timestampNeedsToBeSet = !nextPageUserTaskEntities.isEmpty();

    OffsetDateTime timestamp = timestampNeedsToBeSet ?
      nextPageUserTaskEntities.get(nextPageUserTaskEntities.size() - 1).getEndTime() :
      null;

    if (timestampNeedsToBeSet) {
      importIndexHandler.updatePendingTimestampOfLastEntity(timestamp);
    }

    if (!userTaskEntitiesOfLastTimestamp.isEmpty() || timestampNeedsToBeSet) {
      final List<HistoricUserTaskInstanceDto> allEntities = ImmutableList.<HistoricUserTaskInstanceDto>builder()
        .addAll(userTaskEntitiesOfLastTimestamp)
        .addAll(nextPageUserTaskEntities)
        .build();

      completedUserTaskInstanceImportService.executeImport(allEntities, () -> {
        if (timestampNeedsToBeSet) {
          importIndexHandler.updateTimestampOfLastEntity(timestamp);
        }
      });
    }

    return nextPageUserTaskEntities.size() >= configurationService.getEngineImportUserTaskInstanceMaxPageSize();
  }

}

package org.camunda.optimize.service.engine.importing.service.mediator;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.dto.engine.UserOperationLogEntryEngineDto;
import org.camunda.optimize.plugin.ImportAdapterProvider;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.fetcher.instance.UserOperationLogEntryFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.impl.UserOperationLogInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.TimestampBasedImportPage;
import org.camunda.optimize.service.engine.importing.service.UserOperationLogImportService;
import org.camunda.optimize.service.es.writer.UserOperationsLogEntryWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class UserOperationLogEngineImportMediator
  extends BackoffImportMediator<UserOperationLogInstanceImportIndexHandler> {

  private UserOperationLogEntryFetcher engineEntityFetcher;
  private UserOperationLogImportService userOperationLogImportService;
  @Autowired
  private UserOperationsLogEntryWriter userOperationsLogEntryWriter;
  @Autowired
  private ImportAdapterProvider importAdapterProvider;

  public UserOperationLogEngineImportMediator(EngineContext engineContext) {
    super(engineContext);
  }

  @PostConstruct
  public void init() {
    importIndexHandler = provider.getUserOperationLogImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanFactory.getBean(UserOperationLogEntryFetcher.class, engineContext);
    userOperationLogImportService = new UserOperationLogImportService(
      userOperationsLogEntryWriter, elasticsearchImportJobExecutor, engineContext
    );
  }

  @Override
  protected boolean importNextEnginePage() {
    final List<UserOperationLogEntryEngineDto> entitiesOfLastTimestamp = engineEntityFetcher
      .fetchUserOperationLogEntriesForTimestamp(importIndexHandler.getTimestampOfLastEntity());

    final TimestampBasedImportPage page = importIndexHandler.getNextPage();
    final List<UserOperationLogEntryEngineDto> nextPageEntities =
      engineEntityFetcher.fetchUserOperationLogEntries(page);


    boolean timestampNeedsToBeSet = !nextPageEntities.isEmpty();

    OffsetDateTime timestamp = timestampNeedsToBeSet ?
      nextPageEntities.get(nextPageEntities.size() - 1).getTimestamp() :
      null;


    if (timestampNeedsToBeSet) {
      importIndexHandler.updatePendingTimestampOfLastEntity(timestamp);
    }

    if (!entitiesOfLastTimestamp.isEmpty() || timestampNeedsToBeSet) {
      final List<UserOperationLogEntryEngineDto> allEntities = ImmutableList.<UserOperationLogEntryEngineDto>builder()
        .addAll(entitiesOfLastTimestamp)
        .addAll(nextPageEntities)
        .build();

      userOperationLogImportService.executeImport(allEntities, () -> {
        if (timestampNeedsToBeSet) {
          importIndexHandler.updateTimestampOfLastEntity(timestamp);
        }
      });
    }

    return nextPageEntities.size() >= configurationService.getEngineImportUserOperationLogEntryMaxPageSize();
  }

}

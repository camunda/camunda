package org.camunda.optimize.service.engine.importing.service.mediator;

import org.camunda.optimize.dto.engine.HistoricVariableUpdateInstanceDto;
import org.camunda.optimize.plugin.ImportAdapterProvider;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.fetcher.instance.VariableUpdateInstanceFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.impl.VariableUpdateInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.TimestampBasedImportPage;
import org.camunda.optimize.service.engine.importing.service.VariableUpdateInstanceImportService;
import org.camunda.optimize.service.es.writer.variable.VariableUpdateWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class VariableUpdateEngineImportMediator
  extends BackoffImportMediator<VariableUpdateInstanceImportIndexHandler> {

  protected EngineContext engineContext;
  private VariableUpdateInstanceFetcher engineEntityFetcher;
  private VariableUpdateInstanceImportService variableUpdateInstanceImportService;
  @Autowired
  private VariableUpdateWriter variableWriter;
  @Autowired
  private ImportAdapterProvider importAdapterProvider;

  public VariableUpdateEngineImportMediator(EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @PostConstruct
  public void init() {
      importIndexHandler = provider.getRunningVariableInstanceImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanHelper.getInstance(VariableUpdateInstanceFetcher.class, engineContext);
    variableUpdateInstanceImportService = new VariableUpdateInstanceImportService(
      variableWriter,
      importAdapterProvider,
      elasticsearchImportJobExecutor,
      engineContext
    );
  }

  @Override
  protected boolean importNextEnginePage() {
    List<HistoricVariableUpdateInstanceDto> entitiesOfLastTimestamp =
      engineEntityFetcher.fetchVariableInstanceUpdates(importIndexHandler.getTimestampOfLastEntity());

    TimestampBasedImportPage page = importIndexHandler.getNextPage();
    List<HistoricVariableUpdateInstanceDto> entities =
      engineEntityFetcher.fetchVariableInstanceUpdates(page);
    if (!entities.isEmpty()) {

      OffsetDateTime timestamp = entities.get(entities.size() - 1).getTime();
      importIndexHandler.updateTimestampOfLastEntity(timestamp);
      entities.addAll(entitiesOfLastTimestamp);
      entities = filterLatestUpdates(entities);
      variableUpdateInstanceImportService.executeImport(entities);
    }
    return entities.size() >= configurationService.getEngineImportVariableInstanceMaxPageSize();
  }

  private List<HistoricVariableUpdateInstanceDto> filterLatestUpdates(List<HistoricVariableUpdateInstanceDto> updates) {
    Map<String, HistoricVariableUpdateInstanceDto> latestUpdateForVariable = new HashMap<>();
    for (HistoricVariableUpdateInstanceDto update : updates) {
      latestUpdateForVariable
        .compute(
          update.getVariableInstanceId(),
          (k, v) -> (v != null && v.getTime().isAfter(update.getTime()))? v : update
        );
    }
    return new ArrayList<>(latestUpdateForVariable.values());
  }

}

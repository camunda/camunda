package org.camunda.optimize.service.engine.importing.service.mediator;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.fetcher.instance.CompletedProcessInstanceFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.impl.CompletedProcessInstanceImportIndexHandler;
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
  extends TimestampBasedImportMediator<CompletedProcessInstanceImportIndexHandler, HistoricProcessInstanceDto> {

  private CompletedProcessInstanceFetcher engineEntityFetcher;
  @Autowired
  private CompletedProcessInstanceWriter completedProcessInstanceWriter;

  public CompletedProcessInstanceEngineImportMediator(EngineContext engineContext) {
    super(engineContext);
  }


  @PostConstruct
  public void init() {
    importIndexHandler = provider.getCompletedProcessInstanceImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanFactory.getBean(CompletedProcessInstanceFetcher.class, engineContext);
    importService = new CompletedProcessInstanceImportService(
      completedProcessInstanceWriter, elasticsearchImportJobExecutor, engineContext
    );
  }

  @Override
  protected List<HistoricProcessInstanceDto> getEntitiesLastTimestamp() {
    return engineEntityFetcher.fetchCompletedProcessInstances(importIndexHandler.getTimestampOfLastEntity());

  }

  @Override
  protected List<HistoricProcessInstanceDto> getEntitiesNextPage() {
    return engineEntityFetcher.fetchCompletedProcessInstances(importIndexHandler.getNextPage());
  }

  @Override
  protected int getMaxPageSize() {
    return configurationService.getEngineImportProcessInstanceMaxPageSize();
  }

  @Override
  protected OffsetDateTime getTimestamp(final HistoricProcessInstanceDto historicProcessInstanceDto) {
    return historicProcessInstanceDto.getEndTime();
  }
}

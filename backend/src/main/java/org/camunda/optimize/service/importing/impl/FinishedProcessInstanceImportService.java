package org.camunda.optimize.service.importing.impl;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.service.es.writer.ProcessInstanceWriter;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.importing.diff.MissingProcessInstanceFinder;
import org.camunda.optimize.service.importing.fetcher.DefinitionBasedEngineEntityFetcher;
import org.camunda.optimize.service.importing.index.DefinitionBasedImportIndexHandler;
import org.camunda.optimize.service.importing.job.importing.ProcessInstanceImportJob;
import org.camunda.optimize.service.importing.job.schedule.PageBasedImportScheduleJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class FinishedProcessInstanceImportService
    extends PaginatedImportService<HistoricProcessInstanceDto, ProcessInstanceDto, DefinitionBasedImportIndexHandler> {

  private final Logger logger = LoggerFactory.getLogger(FinishedProcessInstanceImportService.class);

  @Autowired
  private ProcessInstanceWriter processInstanceWriter;

  @Autowired
  private MissingProcessInstanceFinder missingProcessInstanceFinder;

  @Autowired
  private DefinitionBasedEngineEntityFetcher definitionBasedEngineEntityFetcher;

  @Autowired
  public FinishedProcessInstanceImportService(String engineAlias) {
    super(engineAlias);
  }

  @Override
  protected MissingEntitiesFinder<HistoricProcessInstanceDto> getMissingEntitiesFinder() {
    return missingProcessInstanceFinder;
  }

  @Override
  public Class<DefinitionBasedImportIndexHandler> getIndexHandlerType() {
    return DefinitionBasedImportIndexHandler.class;
  }

  @Override
  protected List<HistoricProcessInstanceDto> queryEngineRestPoint(PageBasedImportScheduleJob job) {
    return definitionBasedEngineEntityFetcher.fetchHistoricFinishedProcessInstances(
        job.getCurrentDefinitionBasedImportIndex(),
        configurationService.getEngineImportProcessInstanceMaxPageSize(),
        job.getCurrentProcessDefinitionId(),
        job.getEngineAlias()
    );
  }

  @Override
  public int getEngineEntityCount(DefinitionBasedImportIndexHandler definitionBasedImportIndexHandler, String engineAlias) throws OptimizeException {
    return definitionBasedEngineEntityFetcher
        .fetchHistoricProcessInstanceCount(definitionBasedImportIndexHandler.getAllProcessDefinitions(), engineAlias);
  }

  @Override
  public void importToElasticSearch(List<ProcessInstanceDto> processInstances) {
    ProcessInstanceImportJob importJob = new ProcessInstanceImportJob(processInstanceWriter);
    importJob.setEntitiesToImport(processInstances);
    try {
      importJobExecutor.executeImportJob(importJob);
    } catch (InterruptedException e) {
      logger.error("Interruption during import of process instance job!", e);
    }
  }

  @Override
  public ProcessInstanceDto mapToOptimizeDto(HistoricProcessInstanceDto entry, String engineAlias) {
    final ProcessInstanceDto createEvent = new ProcessInstanceDto();
    mapDefaults(entry, createEvent);
    return createEvent;
  }

  private void mapDefaults(HistoricProcessInstanceDto dto, ProcessInstanceDto procInst) {
    procInst.setProcessDefinitionKey(dto.getProcessDefinitionKey());
    procInst.setProcessDefinitionId(dto.getProcessDefinitionId());
    procInst.setProcessInstanceId(dto.getId());
    procInst.setStartDate(dto.getStartTime());
    procInst.setEndDate(dto.getEndTime());
    procInst.setEngine(getEngineName());
  }

  @Override
  public String getElasticsearchType() {
    return configurationService.getProcessInstanceType();
  }

  @Override
  public boolean isProcessDefinitionBased() {
    return true;
  }

  @Override
  public String getEngineName() {
    return this.engineAlias;
  }

}

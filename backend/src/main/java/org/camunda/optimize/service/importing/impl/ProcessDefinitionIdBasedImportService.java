package org.camunda.optimize.service.importing.impl;

import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.importing.diff.MissingProcessDefinitionFinder;
import org.camunda.optimize.service.importing.fetcher.IdBasedProcessDefinitionFetcher;
import org.camunda.optimize.service.importing.index.DefinitionBasedImportIndexHandler;
import org.camunda.optimize.service.importing.index.ImportIndexHandler;
import org.camunda.optimize.service.importing.job.importing.ProcessDefinitionImportJob;
import org.camunda.optimize.service.importing.job.schedule.PageBasedImportScheduleJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProcessDefinitionIdBasedImportService
    extends PaginatedImportService<ProcessDefinitionEngineDto, ProcessDefinitionOptimizeDto, DefinitionBasedImportIndexHandler> {

  private final Logger logger = LoggerFactory.getLogger(ProcessDefinitionIdBasedImportService.class);

  @Autowired
  private ProcessDefinitionWriter procDefWriter;

  @Autowired
  private MissingProcessDefinitionFinder processDefinitionFinder;

  @Autowired
  private IdBasedProcessDefinitionFetcher idBasedProcessDefinitionFetcher;


  @Override
  protected MissingEntitiesFinder<ProcessDefinitionEngineDto> getMissingEntitiesFinder() {
    return processDefinitionFinder;
  }

  @Override
  public Class<DefinitionBasedImportIndexHandler> getIndexHandlerType() {
    return DefinitionBasedImportIndexHandler.class;
  }

  @Override
  protected List<ProcessDefinitionEngineDto> queryEngineRestPoint(PageBasedImportScheduleJob job) throws OptimizeException {
    return idBasedProcessDefinitionFetcher.fetchProcessDefinitions(
        job.getCurrentDefinitionBasedImportIndex(),
        job.getCurrentProcessDefinitionId()
    );
  }

  @Override
  public int getEngineEntityCount(DefinitionBasedImportIndexHandler definitionBasedImportIndexHandler) throws OptimizeException {
    return idBasedProcessDefinitionFetcher
        .fetchProcessDefinitionCount(definitionBasedImportIndexHandler.getAllProcessDefinitions());
  }

  @Override
  public void importToElasticSearch(List<ProcessDefinitionOptimizeDto> optimizeEntries) {
    ProcessDefinitionImportJob procDefImportJob = new ProcessDefinitionImportJob(procDefWriter);
    procDefImportJob.setEntitiesToImport(optimizeEntries);
    try {
      importJobExecutor.executeImportJob(procDefImportJob);
    } catch (InterruptedException e) {
      logger.error("Interruption during import of process definition import job!", e);
    }
  }

  @Override
  public ProcessDefinitionOptimizeDto mapToOptimizeDto(ProcessDefinitionEngineDto entry) {
    return mapDefaults(entry);
  }

  private ProcessDefinitionOptimizeDto mapDefaults(ProcessDefinitionEngineDto dto) {
    ProcessDefinitionOptimizeDto optimizeDto = new ProcessDefinitionOptimizeDto();
    optimizeDto.setName(dto.getName());
    optimizeDto.setKey(dto.getKey());
    optimizeDto.setId(dto.getId());
    optimizeDto.setVersion(dto.getVersion());
    return optimizeDto;
  }

  @Override
  public String getElasticsearchType() {
    return configurationService.getProcessDefinitionType();
  }

  @Override
  public boolean isProcessDefinitionBased() {
    return true;
  }
}

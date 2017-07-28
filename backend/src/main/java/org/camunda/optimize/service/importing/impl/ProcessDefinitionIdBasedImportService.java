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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProcessDefinitionIdBasedImportService extends PaginatedImportService<ProcessDefinitionEngineDto, ProcessDefinitionOptimizeDto> {

  private final Logger logger = LoggerFactory.getLogger(ProcessDefinitionIdBasedImportService.class);

  @Autowired
  private ProcessDefinitionWriter procDefWriter;

  @Autowired
  private MissingProcessDefinitionFinder processDefinitionFinder;

  @Autowired
  private IdBasedProcessDefinitionFetcher idBasedProcessDefinitionFetcher;

  @Autowired
  private DefinitionBasedImportIndexHandler definitionBasedImportIndexHandler;

  @Override
  protected ImportIndexHandler initializeImportIndexHandler() {
    definitionBasedImportIndexHandler.initializeImportIndex(getElasticsearchType());
    return definitionBasedImportIndexHandler;
  }

  @Override
  protected MissingEntitiesFinder<ProcessDefinitionEngineDto> getMissingEntitiesFinder() {
    return processDefinitionFinder;
  }

  @Override
  protected List<ProcessDefinitionEngineDto> queryEngineRestPoint() throws OptimizeException {
    return idBasedProcessDefinitionFetcher.fetchProcessDefinitions(
      definitionBasedImportIndexHandler.getCurrentDefinitionBasedImportIndex(),
      definitionBasedImportIndexHandler.getCurrentProcessDefinitionId()
    );
  }

  @Override
  public int getEngineEntityCount() throws OptimizeException {
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
}

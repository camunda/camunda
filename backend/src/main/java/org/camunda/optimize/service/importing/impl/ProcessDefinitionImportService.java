package org.camunda.optimize.service.importing.impl;

import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.importing.diff.MissingProcessDefinitionFinder;
import org.camunda.optimize.service.importing.fetcher.AllEntitiesSetBasedEngineEntityFetcher;
import org.camunda.optimize.service.importing.index.AllEntitiesBasedImportIndexHandler;
import org.camunda.optimize.service.importing.job.importing.ProcessDefinitionImportJob;
import org.camunda.optimize.service.importing.job.schedule.PageBasedImportScheduleJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class ProcessDefinitionImportService
    extends PaginatedImportService<ProcessDefinitionEngineDto, ProcessDefinitionOptimizeDto, AllEntitiesBasedImportIndexHandler> {

  private final Logger logger = LoggerFactory.getLogger(ProcessDefinitionImportService.class);

  @Autowired
  private ProcessDefinitionWriter procDefWriter;

  @Autowired
  private MissingProcessDefinitionFinder processDefinitionFinder;

  @Autowired
  private AllEntitiesSetBasedEngineEntityFetcher engineEntityFetcher;

  @Autowired
  public ProcessDefinitionImportService(String engineAlias) {
    super(engineAlias);
  }

  @Override
  protected MissingEntitiesFinder<ProcessDefinitionEngineDto> getMissingEntitiesFinder() {
    return processDefinitionFinder;
  }

  @Override
  public Class<AllEntitiesBasedImportIndexHandler> getIndexHandlerType() {
    return AllEntitiesBasedImportIndexHandler.class;
  }

  @Override
  protected List<ProcessDefinitionEngineDto> queryEngineRestPoint(PageBasedImportScheduleJob job) throws OptimizeException {
    return engineEntityFetcher.fetchProcessDefinitions(
      job.getAbsoluteImportIndex(),
      job.getEngineAlias()
    );
  }

  @Override
  public int getEngineEntityCount(AllEntitiesBasedImportIndexHandler indexHandler, String engineAlias) throws OptimizeException {
    return engineEntityFetcher.fetchProcessDefinitionCount(engineAlias);
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
  public ProcessDefinitionOptimizeDto mapToOptimizeDto(ProcessDefinitionEngineDto entry, String engineAlias) {
    ProcessDefinitionOptimizeDto processDefinitionOptimizeDto = mapDefaults(entry);
    processDefinitionOptimizeDto.setEngine(engineAlias);
    return processDefinitionOptimizeDto;
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

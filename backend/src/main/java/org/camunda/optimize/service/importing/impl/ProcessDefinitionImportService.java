package org.camunda.optimize.service.importing.impl;

import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.importing.diff.MissingProcessDefinitionFinder;
import org.camunda.optimize.service.importing.job.impl.ProcessDefinitionImportJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ProcessDefinitionImportService extends PaginatedImportService<ProcessDefinitionEngineDto, ProcessDefinitionOptimizeDto> {

  private final Logger logger = LoggerFactory.getLogger(ProcessDefinitionImportService.class);

  @Autowired
  private ProcessDefinitionWriter procDefWriter;

  @Autowired
  private MissingProcessDefinitionFinder processDefinitionFinder;

  @Override
  protected MissingEntitiesFinder<ProcessDefinitionEngineDto> getMissingEntitiesFinder() {
    return processDefinitionFinder;
  }

  @Override
  protected List<ProcessDefinitionEngineDto> queryEngineRestPoint(int indexOfFirstResult, int maxPageSize) {
    return engineEntityFetcher.fetchProcessDefinitions(indexOfFirstResult, maxPageSize);
  }

  @Override
  public void importToElasticSearch(List<ProcessDefinitionOptimizeDto> optimizeEntries) {
    ProcessDefinitionImportJob procDefImportJob = new ProcessDefinitionImportJob(procDefWriter);
    procDefImportJob.addEntitiesToImport(optimizeEntries);
    try {
      importJobExecutor.addNewImportJob(procDefImportJob);
    } catch (InterruptedException e) {
      logger.error("Interruption during import of process definition import job!", e);
    }
  }

  @Override
  public List<ProcessDefinitionOptimizeDto> mapToOptimizeDto(List<ProcessDefinitionEngineDto> entries) {
    List<ProcessDefinitionOptimizeDto> result = new ArrayList<>(entries.size());
    for (ProcessDefinitionEngineDto entry : entries) {
      mapDefaults(entry);
      result.add(mapDefaults(entry));
    }

    return result;
  }

  private ProcessDefinitionOptimizeDto mapDefaults(ProcessDefinitionEngineDto dto) {
    ProcessDefinitionOptimizeDto optimizeDto = new ProcessDefinitionOptimizeDto();
    optimizeDto.setName(dto.getName());
    optimizeDto.setKey(dto.getKey());
    optimizeDto.setId(dto.getId());
    return optimizeDto;
  }
}

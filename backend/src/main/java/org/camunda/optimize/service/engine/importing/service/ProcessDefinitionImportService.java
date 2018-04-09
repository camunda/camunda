package org.camunda.optimize.service.engine.importing.service;

import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.ProcessDefinitionElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;

import java.util.List;

public class ProcessDefinitionImportService extends
  ImportService<ProcessDefinitionEngineDto, ProcessDefinitionOptimizeDto> {

  private ProcessDefinitionWriter processDefinitionWriter;

  public ProcessDefinitionImportService(
      ProcessDefinitionWriter processDefinitionWriter,
      ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
      MissingEntitiesFinder<ProcessDefinitionEngineDto> missingEntitiesFinder,
      EngineContext engineContext
  ) {
    super(elasticsearchImportJobExecutor, missingEntitiesFinder, engineContext);
    this.processDefinitionWriter = processDefinitionWriter;

  }

  @Override
  protected ElasticsearchImportJob<ProcessDefinitionOptimizeDto>
  createElasticsearchImportJob(List<ProcessDefinitionOptimizeDto> processDefinitions) {
    ProcessDefinitionElasticsearchImportJob procDefImportJob = new ProcessDefinitionElasticsearchImportJob(processDefinitionWriter);
    procDefImportJob.setEntitiesToImport(processDefinitions);
    return procDefImportJob;
  }

  @Override
  protected ProcessDefinitionOptimizeDto mapEngineEntityToOptimizeEntity(ProcessDefinitionEngineDto engineEntity) {
    ProcessDefinitionOptimizeDto optimizeDto = new ProcessDefinitionOptimizeDto();
    optimizeDto.setName(engineEntity.getName());
    optimizeDto.setKey(engineEntity.getKey());
    optimizeDto.setId(engineEntity.getId());
    optimizeDto.setVersion(engineEntity.getVersion());
    optimizeDto.setEngine(engineContext.getEngineAlias());
    return optimizeDto;
  }

}

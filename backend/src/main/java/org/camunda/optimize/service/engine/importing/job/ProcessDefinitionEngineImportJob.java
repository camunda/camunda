package org.camunda.optimize.service.engine.importing.job;

import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.engine.importing.fetcher.instance.EngineEntityFetcher;
import org.camunda.optimize.service.engine.importing.index.page.AllEntitiesBasedImportPage;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.engine.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.ProcessDefinitionElasticsearchImportJob;

import java.util.List;

public class ProcessDefinitionEngineImportJob extends
  EngineImportJob<ProcessDefinitionEngineDto, ProcessDefinitionOptimizeDto, AllEntitiesBasedImportPage> {

  private ProcessDefinitionWriter processDefinitionWriter;

  public ProcessDefinitionEngineImportJob(ProcessDefinitionWriter processDefinitionWriter,
                                          AllEntitiesBasedImportPage importIndex,
                                          ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
                                          MissingEntitiesFinder<ProcessDefinitionEngineDto> missingEntitiesFinder,
                                          EngineEntityFetcher<ProcessDefinitionEngineDto,AllEntitiesBasedImportPage> engineEntityFetcher) {
    super(importIndex, elasticsearchImportJobExecutor, missingEntitiesFinder, engineEntityFetcher);
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
    optimizeDto.setEngine("1");
    return optimizeDto;
  }

}

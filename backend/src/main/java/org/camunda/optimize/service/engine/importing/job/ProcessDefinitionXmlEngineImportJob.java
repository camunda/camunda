package org.camunda.optimize.service.engine.importing.job;

import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionXmlOptimizeDto;
import org.camunda.optimize.service.engine.importing.fetcher.instance.EngineEntityFetcher;
import org.camunda.optimize.service.engine.importing.index.page.AllEntitiesBasedImportPage;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.engine.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.ProcessDefinitionXmlElasticsearchImportJob;

import java.util.List;

public class ProcessDefinitionXmlEngineImportJob extends
  EngineImportJob<ProcessDefinitionXmlEngineDto, ProcessDefinitionXmlOptimizeDto, AllEntitiesBasedImportPage> {

  private ProcessDefinitionWriter processDefinitionWriter;

  public ProcessDefinitionXmlEngineImportJob(ProcessDefinitionWriter processDefinitionWriter,
                                             AllEntitiesBasedImportPage importIndex,
                                             ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
                                             MissingEntitiesFinder<ProcessDefinitionXmlEngineDto> missingEntitiesFinder,
                                             EngineEntityFetcher<ProcessDefinitionXmlEngineDto,
                                             AllEntitiesBasedImportPage> engineEntityFetcher,
                                             String engineAlias
                                             ) {
    super(importIndex, elasticsearchImportJobExecutor, missingEntitiesFinder, engineEntityFetcher, engineAlias);
    this.processDefinitionWriter = processDefinitionWriter;

  }

  @Override
  protected ElasticsearchImportJob<ProcessDefinitionXmlOptimizeDto>
        createElasticsearchImportJob(List<ProcessDefinitionXmlOptimizeDto> processDefinitions) {
    ProcessDefinitionXmlElasticsearchImportJob procDefImportJob = new ProcessDefinitionXmlElasticsearchImportJob(processDefinitionWriter);
    procDefImportJob.setEntitiesToImport(processDefinitions);
    return procDefImportJob;
  }

  @Override
  protected ProcessDefinitionXmlOptimizeDto mapEngineEntityToOptimizeEntity(ProcessDefinitionXmlEngineDto engineEntity) {
    ProcessDefinitionXmlOptimizeDto optimizeDto = new ProcessDefinitionXmlOptimizeDto();
    optimizeDto.setBpmn20Xml(engineEntity.getBpmn20Xml());
    optimizeDto.setId(engineEntity.getId());
    optimizeDto.setEngine(engineAlias);
    return optimizeDto;
  }

}

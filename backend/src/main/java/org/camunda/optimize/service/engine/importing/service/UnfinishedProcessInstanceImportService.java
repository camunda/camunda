package org.camunda.optimize.service.engine.importing.service;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.UnfinishedProcessInstanceElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.UnfinishedProcessInstanceWriter;

import java.util.List;

public class UnfinishedProcessInstanceImportService
    extends ImportService<HistoricProcessInstanceDto, ProcessInstanceDto> {

  private UnfinishedProcessInstanceWriter unfinishedProcessInstanceWriter;

  public UnfinishedProcessInstanceImportService(
      UnfinishedProcessInstanceWriter unfinishedProcessInstanceWriter,
      ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
      MissingEntitiesFinder<HistoricProcessInstanceDto> missingEntitiesFinder,
      EngineContext engineContext
  ) {
    super(elasticsearchImportJobExecutor, missingEntitiesFinder, engineContext);
    this.unfinishedProcessInstanceWriter = unfinishedProcessInstanceWriter;
  }

  @Override
  protected ElasticsearchImportJob<ProcessInstanceDto> createElasticsearchImportJob(List<ProcessInstanceDto> processInstances) {
    UnfinishedProcessInstanceElasticsearchImportJob importJob =
        new UnfinishedProcessInstanceElasticsearchImportJob(unfinishedProcessInstanceWriter);
    importJob.setEntitiesToImport(processInstances);
    return importJob;
  }

  @Override
  protected ProcessInstanceDto mapEngineEntityToOptimizeEntity(HistoricProcessInstanceDto engineEntity) {
    ProcessInstanceDto processInstanceDto = new ProcessInstanceDto();
    processInstanceDto.setProcessDefinitionKey(engineEntity.getProcessDefinitionKey());
    processInstanceDto.setProcessDefinitionVersion(engineEntity.getProcessDefinitionVersionAsString());
    processInstanceDto.setProcessDefinitionId(engineEntity.getProcessDefinitionId());
    processInstanceDto.setProcessInstanceId(engineEntity.getId());
    processInstanceDto.setStartDate(engineEntity.getStartTime());
    processInstanceDto.setEndDate(null);
    processInstanceDto.setEngine(engineContext.getEngineAlias());
    return processInstanceDto;
  }

}

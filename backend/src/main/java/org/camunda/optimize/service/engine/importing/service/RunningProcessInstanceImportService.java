package org.camunda.optimize.service.engine.importing.service;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.RunningProcessInstanceElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.RunningProcessInstanceWriter;

import java.util.List;

public class RunningProcessInstanceImportService
    extends ImportService<HistoricProcessInstanceDto, ProcessInstanceDto> {

  private RunningProcessInstanceWriter runningProcessInstanceWriter;

  public RunningProcessInstanceImportService(
      RunningProcessInstanceWriter runningProcessInstanceWriter,
      ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
      MissingEntitiesFinder<HistoricProcessInstanceDto> missingEntitiesFinder,
      EngineContext engineContext
  ) {
    super(elasticsearchImportJobExecutor, missingEntitiesFinder, engineContext);
    this.runningProcessInstanceWriter = runningProcessInstanceWriter;
  }

  @Override
  protected ElasticsearchImportJob<ProcessInstanceDto> createElasticsearchImportJob(List<ProcessInstanceDto> processInstances) {
    RunningProcessInstanceElasticsearchImportJob importJob =
        new RunningProcessInstanceElasticsearchImportJob(runningProcessInstanceWriter);
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

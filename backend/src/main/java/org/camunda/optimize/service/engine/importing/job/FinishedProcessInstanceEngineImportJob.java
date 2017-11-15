package org.camunda.optimize.service.engine.importing.job;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.service.engine.importing.fetcher.instance.EngineEntityFetcher;
import org.camunda.optimize.service.engine.importing.index.page.DefinitionBasedImportPage;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.FinishedProcessInstanceWriter;
import org.camunda.optimize.service.engine.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.FinishedProcessInstanceElasticsearchImportJob;

import java.util.List;

public class FinishedProcessInstanceEngineImportJob
  extends EngineImportJob<HistoricProcessInstanceDto, ProcessInstanceDto, DefinitionBasedImportPage> {

  private FinishedProcessInstanceWriter finishedProcessInstanceWriter;

  public FinishedProcessInstanceEngineImportJob(FinishedProcessInstanceWriter finishedProcessInstanceWriter,
                                                DefinitionBasedImportPage importIndex,
                                                ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
                                                MissingEntitiesFinder<HistoricProcessInstanceDto> missingEntitiesFinder,
                                                EngineEntityFetcher<HistoricProcessInstanceDto, DefinitionBasedImportPage> engineEntityFetcher) {
    super(importIndex, elasticsearchImportJobExecutor, missingEntitiesFinder, engineEntityFetcher);
    this.finishedProcessInstanceWriter = finishedProcessInstanceWriter;
  }

  @Override
  protected ElasticsearchImportJob<ProcessInstanceDto> createElasticsearchImportJob(List<ProcessInstanceDto> processInstances) {
    FinishedProcessInstanceElasticsearchImportJob importJob = new FinishedProcessInstanceElasticsearchImportJob(finishedProcessInstanceWriter);
    importJob.setEntitiesToImport(processInstances);
    return importJob;
  }

  @Override
  protected ProcessInstanceDto mapEngineEntityToOptimizeEntity(HistoricProcessInstanceDto engineEntity) {
    ProcessInstanceDto processInstanceDto = new ProcessInstanceDto();
    processInstanceDto.setProcessDefinitionKey(engineEntity.getProcessDefinitionKey());
    processInstanceDto.setProcessDefinitionId(engineEntity.getProcessDefinitionId());
    processInstanceDto.setProcessInstanceId(engineEntity.getId());
    processInstanceDto.setStartDate(engineEntity.getStartTime());
    processInstanceDto.setEndDate(engineEntity.getEndTime());
    processInstanceDto.setEngine("1");
    return processInstanceDto;
  }

}

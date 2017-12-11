package org.camunda.optimize.service.engine.importing.job;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.fetcher.instance.EngineEntityFetcher;
import org.camunda.optimize.service.engine.importing.index.page.DefinitionBasedImportPage;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.FinishedProcessInstanceWriter;
import org.camunda.optimize.service.engine.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.FinishedProcessInstanceElasticsearchImportJob;

import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;

public class FinishedProcessInstanceEngineImportJob
  extends EngineImportJob<HistoricProcessInstanceDto, ProcessInstanceDto, DefinitionBasedImportPage> {

  private FinishedProcessInstanceWriter finishedProcessInstanceWriter;

  public FinishedProcessInstanceEngineImportJob(FinishedProcessInstanceWriter finishedProcessInstanceWriter,
                                                DefinitionBasedImportPage importIndex,
                                                ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
                                                MissingEntitiesFinder<HistoricProcessInstanceDto> missingEntitiesFinder,
                                                EngineEntityFetcher<HistoricProcessInstanceDto,
                                                DefinitionBasedImportPage> engineEntityFetcher,
                                                EngineContext engineContext
                                                ) {
    super(importIndex, elasticsearchImportJobExecutor, missingEntitiesFinder, engineEntityFetcher, engineContext);
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
    OffsetDateTime startDate = engineEntity.getStartTime();
    OffsetDateTime endDate = engineEntity.getEndTime();
    processInstanceDto.setStartDate(startDate);
    processInstanceDto.setEndDate(endDate);
    processInstanceDto.setDurationInMs(endDate.toInstant().toEpochMilli() - startDate.toInstant().toEpochMilli());
    processInstanceDto.setEngine(this.engineContext.getEngineAlias());
    return processInstanceDto;
  }

}

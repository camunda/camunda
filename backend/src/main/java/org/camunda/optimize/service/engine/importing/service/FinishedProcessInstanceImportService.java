package org.camunda.optimize.service.engine.importing.service;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.FinishedProcessInstanceElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.FinishedProcessInstanceWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class FinishedProcessInstanceImportService {

  protected Logger logger = LoggerFactory.getLogger(getClass());

  protected ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  protected EngineContext engineContext;
  private FinishedProcessInstanceWriter finishedProcessInstanceWriter;

  public FinishedProcessInstanceImportService(FinishedProcessInstanceWriter finishedProcessInstanceWriter,
                                              ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
                                              EngineContext engineContext
                                                ) {
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
    this.engineContext = engineContext;
    this.finishedProcessInstanceWriter = finishedProcessInstanceWriter;
  }

  public void executeImport(List<HistoricProcessInstanceDto> pageOfEngineEntities) {
    logger.trace("Importing entities from engine...");

    boolean newDataIsAvailable = !pageOfEngineEntities.isEmpty();
    if (newDataIsAvailable) {
      List<ProcessInstanceDto> newOptimizeEntities = mapEngineEntitiesToOptimizeEntities(pageOfEngineEntities);
      ElasticsearchImportJob<ProcessInstanceDto> elasticsearchImportJob =
        createElasticsearchImportJob(newOptimizeEntities);
      addElasticsearchImportJobToQueue(elasticsearchImportJob);
    }
  }

  private void addElasticsearchImportJobToQueue(ElasticsearchImportJob elasticsearchImportJob) {
    try {
      elasticsearchImportJobExecutor.executeImportJob(elasticsearchImportJob);
    } catch (InterruptedException e) {
      logger.error("Was interrupted while trying to add new job to Elasticsearch import queue.", e);
    }
  }

  private List<ProcessInstanceDto> mapEngineEntitiesToOptimizeEntities(List<HistoricProcessInstanceDto> engineEntities) {
    return engineEntities
      .stream().map(this::mapEngineEntityToOptimizeEntity)
      .collect(Collectors.toList());
  }

  private ElasticsearchImportJob<ProcessInstanceDto> createElasticsearchImportJob(List<ProcessInstanceDto> processInstances) {
    FinishedProcessInstanceElasticsearchImportJob importJob = new FinishedProcessInstanceElasticsearchImportJob(finishedProcessInstanceWriter);
    importJob.setEntitiesToImport(processInstances);
    return importJob;
  }

  private ProcessInstanceDto mapEngineEntityToOptimizeEntity(HistoricProcessInstanceDto engineEntity) {
    ProcessInstanceDto processInstanceDto = new ProcessInstanceDto();
    processInstanceDto.setProcessDefinitionKey(engineEntity.getProcessDefinitionKey());
    processInstanceDto.setProcessDefinitionVersion(engineEntity.getProcessDefinitionVersionAsString());
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

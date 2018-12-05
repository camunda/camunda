package org.camunda.optimize.service.engine.importing.service;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.RunningActivityInstanceElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.RunningActivityInstanceWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class RunningActivityInstanceImportService {

  protected Logger logger = LoggerFactory.getLogger(getClass());

  protected ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  protected EngineContext engineContext;
  private RunningActivityInstanceWriter runningActivityInstanceWriter;

  public RunningActivityInstanceImportService(RunningActivityInstanceWriter runningActivityInstanceWriter,
                                              ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
                                              EngineContext engineContext
  ) {
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
    this.engineContext = engineContext;
    this.runningActivityInstanceWriter = runningActivityInstanceWriter;
  }

  public void executeImport(List<HistoricActivityInstanceEngineDto> pageOfEngineEntities) {
    logger.trace("Importing running activity instances from engine...");

    boolean newDataIsAvailable = !pageOfEngineEntities.isEmpty();
    if (newDataIsAvailable) {
      List<FlowNodeEventDto> newOptimizeEntities = mapEngineEntitiesToOptimizeEntities(pageOfEngineEntities);
      ElasticsearchImportJob<FlowNodeEventDto> elasticsearchImportJob =
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

  private List<FlowNodeEventDto> mapEngineEntitiesToOptimizeEntities(List<HistoricActivityInstanceEngineDto> engineEntities) {
    return engineEntities
      .stream().map(this::mapEngineEntityToOptimizeEntity)
      .collect(Collectors.toList());
  }

  private ElasticsearchImportJob<FlowNodeEventDto> createElasticsearchImportJob(List<FlowNodeEventDto> events) {
    RunningActivityInstanceElasticsearchImportJob activityImportJob =
      new RunningActivityInstanceElasticsearchImportJob(runningActivityInstanceWriter);
    activityImportJob.setEntitiesToImport(events);
    return activityImportJob;
  }

  private FlowNodeEventDto mapEngineEntityToOptimizeEntity(HistoricActivityInstanceEngineDto engineEntity) {
    FlowNodeEventDto flowNodeEventDto = new FlowNodeEventDto();
    flowNodeEventDto.setId(engineEntity.getId());
    flowNodeEventDto.setActivityId(engineEntity.getActivityId());
    flowNodeEventDto.setActivityInstanceId(engineEntity.getParentActivityInstanceId());
    flowNodeEventDto.setTimestamp(engineEntity.getStartTime());
    flowNodeEventDto.setProcessDefinitionKey(engineEntity.getProcessDefinitionKey());
    flowNodeEventDto.setProcessDefinitionId(engineEntity.getProcessDefinitionId());
    flowNodeEventDto.setProcessInstanceId(engineEntity.getProcessInstanceId());
    flowNodeEventDto.setStartDate(engineEntity.getStartTime());
    flowNodeEventDto.setEndDate(null);
    flowNodeEventDto.setActivityType(engineEntity.getActivityType());
    flowNodeEventDto.setDurationInMs(null);
    flowNodeEventDto.setEngineAlias(engineContext.getEngineAlias());
    return flowNodeEventDto;
  }

}

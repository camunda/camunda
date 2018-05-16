package org.camunda.optimize.service.engine.importing.service;

import org.camunda.optimize.dto.engine.HistoricVariableUpdateInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.VariableUpdateElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.variable.VariableUpdateWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class VariableUpdateInstanceImportService {

  protected Logger logger = LoggerFactory.getLogger(getClass());

  protected ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  protected EngineContext engineContext;
  private VariableUpdateWriter variableWriter;

  public VariableUpdateInstanceImportService(
      VariableUpdateWriter variableWriter,
      ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
      EngineContext engineContext
  ) {
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
    this.engineContext = engineContext;
    this.variableWriter = variableWriter;
  }

  public void executeImport(List<HistoricVariableUpdateInstanceDto> pageOfEngineEntities) {
    logger.trace("Importing entities from engine...");

    boolean newDataIsAvailable = !pageOfEngineEntities.isEmpty();
    if (newDataIsAvailable) {
      List<VariableDto> newOptimizeEntities = mapEngineEntitiesToOptimizeEntities(pageOfEngineEntities);
      ElasticsearchImportJob<VariableDto> elasticsearchImportJob =
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

  private List<VariableDto> mapEngineEntitiesToOptimizeEntities(List<HistoricVariableUpdateInstanceDto> engineEntities) {
    return engineEntities
      .stream().map(this::mapEngineEntityToOptimizeEntity)
      .collect(Collectors.toList());
  }

  private ElasticsearchImportJob<VariableDto> createElasticsearchImportJob(List<VariableDto> processInstances) {
    VariableUpdateElasticsearchImportJob importJob =
        new VariableUpdateElasticsearchImportJob(variableWriter);
    importJob.setEntitiesToImport(processInstances);
    return importJob;
  }

  private VariableDto mapEngineEntityToOptimizeEntity(HistoricVariableUpdateInstanceDto engineEntity) {
    VariableDto optimizeDto = new VariableDto();
    optimizeDto.setId(engineEntity.getVariableInstanceId());
    optimizeDto.setName(engineEntity.getVariableName());
    optimizeDto.setType(engineEntity.getVariableType());
    optimizeDto.setValue(engineEntity.getValue());

    optimizeDto.setProcessDefinitionId(engineEntity.getProcessDefinitionId());
    optimizeDto.setProcessDefinitionKey(engineEntity.getProcessDefinitionKey());
    optimizeDto.setProcessInstanceId(engineEntity.getProcessInstanceId());

    return optimizeDto;
  }

}

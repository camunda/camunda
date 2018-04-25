package org.camunda.optimize.service.engine.importing.service;

import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.ProcessDefinitionElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class ProcessDefinitionImportService {

  protected Logger logger = LoggerFactory.getLogger(getClass());

  protected ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private MissingEntitiesFinder<ProcessDefinitionEngineDto> missingProcessDefinitionFinder;
  protected EngineContext engineContext;
  private ProcessDefinitionWriter processDefinitionWriter;

  public ProcessDefinitionImportService(
      ProcessDefinitionWriter processDefinitionWriter,
      ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
      MissingEntitiesFinder<ProcessDefinitionEngineDto> missingProcessDefinitionFinder,
      EngineContext engineContext
  ) {
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
    this.missingProcessDefinitionFinder = missingProcessDefinitionFinder;
    this.engineContext = engineContext;
    this.processDefinitionWriter = processDefinitionWriter;

  }

  public void executeImport(List<ProcessDefinitionEngineDto> pageOfEngineEntities) {
    logger.trace("Importing entities from engine...");

    List<ProcessDefinitionEngineDto> newEngineEntities =
          missingProcessDefinitionFinder.retrieveMissingEntities(pageOfEngineEntities);
    boolean newDataIsAvailable = !newEngineEntities.isEmpty();
    if (newDataIsAvailable) {
      List<ProcessDefinitionOptimizeDto> newOptimizeEntities = mapEngineEntitiesToOptimizeEntities(newEngineEntities);
      ElasticsearchImportJob<ProcessDefinitionOptimizeDto> elasticsearchImportJob =
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

  private List<ProcessDefinitionOptimizeDto> mapEngineEntitiesToOptimizeEntities(List<ProcessDefinitionEngineDto> engineEntities) {
    return engineEntities
      .stream().map(this::mapEngineEntityToOptimizeEntity)
      .collect(Collectors.toList());
  }

  private ElasticsearchImportJob<ProcessDefinitionOptimizeDto>
  createElasticsearchImportJob(List<ProcessDefinitionOptimizeDto> processDefinitions) {
    ProcessDefinitionElasticsearchImportJob procDefImportJob = new ProcessDefinitionElasticsearchImportJob(processDefinitionWriter);
    procDefImportJob.setEntitiesToImport(processDefinitions);
    return procDefImportJob;
  }

  private ProcessDefinitionOptimizeDto mapEngineEntityToOptimizeEntity(ProcessDefinitionEngineDto engineEntity) {
    ProcessDefinitionOptimizeDto optimizeDto = new ProcessDefinitionOptimizeDto();
    optimizeDto.setName(engineEntity.getName());
    optimizeDto.setKey(engineEntity.getKey());
    optimizeDto.setId(engineEntity.getId());
    optimizeDto.setVersion(engineEntity.getVersion());
    optimizeDto.setEngine(engineContext.getEngineAlias());
    return optimizeDto;
  }

}

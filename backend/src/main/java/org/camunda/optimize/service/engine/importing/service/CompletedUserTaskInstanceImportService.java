package org.camunda.optimize.service.engine.importing.service;

import org.camunda.optimize.dto.engine.HistoricUserTaskInstanceDto;
import org.camunda.optimize.dto.optimize.importing.UserTaskInstanceDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.CompletedUserTasksElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.CompletedUserTaskInstanceWriter;
import org.camunda.optimize.service.exceptions.OptimizeProcessDefinitionFetchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class CompletedUserTaskInstanceImportService {
  private static final Logger logger = LoggerFactory.getLogger(CompletedUserTaskInstanceImportService.class);

  private final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private final EngineContext engineContext;
  private final CompletedUserTaskInstanceWriter completedProcessInstanceWriter;
  private final ProcessDefinitionVersionResolverService processDefinitionVersionResolverService;

  public CompletedUserTaskInstanceImportService(final CompletedUserTaskInstanceWriter completedProcessInstanceWriter,
                                                final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
                                                final EngineContext engineContext,
                                                final ProcessDefinitionVersionResolverService processDefinitionVersionResolverService) {
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
    this.engineContext = engineContext;
    this.completedProcessInstanceWriter = completedProcessInstanceWriter;
    this.processDefinitionVersionResolverService = processDefinitionVersionResolverService;
  }

  public void executeImport(final List<HistoricUserTaskInstanceDto> pageOfEngineEntities)
    throws OptimizeProcessDefinitionFetchException {
    logger.trace("Importing completed user task entities from engine...");

    final boolean newDataIsAvailable = !pageOfEngineEntities.isEmpty();
    if (newDataIsAvailable) {
      final List<UserTaskInstanceDto> newOptimizeEntities = mapEngineEntitiesToOptimizeEntities(pageOfEngineEntities);
      final ElasticsearchImportJob<UserTaskInstanceDto> elasticsearchImportJob = createElasticsearchImportJob(
        newOptimizeEntities);
      addElasticsearchImportJobToQueue(elasticsearchImportJob);
    }
  }

  private void addElasticsearchImportJobToQueue(final ElasticsearchImportJob elasticsearchImportJob) {
    try {
      elasticsearchImportJobExecutor.executeImportJob(elasticsearchImportJob);
    } catch (InterruptedException e) {
      logger.error("Was interrupted while trying to add new job to Elasticsearch import queue.", e);
    }
  }

  private List<UserTaskInstanceDto> mapEngineEntitiesToOptimizeEntities(final List<HistoricUserTaskInstanceDto> engineEntities)
    throws OptimizeProcessDefinitionFetchException {
    List<UserTaskInstanceDto> list = new ArrayList<>();
    for (HistoricUserTaskInstanceDto engineEntity : engineEntities) {
      UserTaskInstanceDto userTaskInstanceDto = mapEngineEntityToOptimizeEntity(engineEntity);
      list.add(userTaskInstanceDto);
    }
    return list;
  }

  private ElasticsearchImportJob<UserTaskInstanceDto> createElasticsearchImportJob(final List<UserTaskInstanceDto> userTasks) {
    final CompletedUserTasksElasticsearchImportJob importJob = new CompletedUserTasksElasticsearchImportJob(
      completedProcessInstanceWriter
    );
    importJob.setEntitiesToImport(userTasks);
    return importJob;
  }

  private UserTaskInstanceDto mapEngineEntityToOptimizeEntity(final HistoricUserTaskInstanceDto engineEntity)
    throws OptimizeProcessDefinitionFetchException {
    final UserTaskInstanceDto userTaskInstanceDto = new UserTaskInstanceDto(
      engineEntity.getId(),
      engineEntity.getProcessDefinitionId(),
      engineEntity.getProcessDefinitionKey(),
      resolveProcessDefinitionVersion(engineEntity),
      engineEntity.getProcessInstanceId(),
      engineEntity.getTaskDefinitionKey(),
      engineEntity.getActivityInstanceId(),
      engineEntity.getStartTime(),
      engineEntity.getEndTime(),
      engineEntity.getDue(),
      engineEntity.getDeleteReason(),
      engineEntity.getDuration(),
      engineContext.getEngineAlias()
    );
    return userTaskInstanceDto;
  }

  private String resolveProcessDefinitionVersion(final HistoricUserTaskInstanceDto engineEntity)
    throws OptimizeProcessDefinitionFetchException {
    return processDefinitionVersionResolverService
      .getVersionForProcessDefinitionId(engineEntity.getProcessDefinitionId())
      .orElseThrow(() -> {
        final String message = String.format(
          "Couldn't obtain version for processDefinitionId [%s]. It hasn't been imported yet",
          engineEntity.getProcessDefinitionId()
        );
        return new OptimizeProcessDefinitionFetchException(message);
      });
  }

}

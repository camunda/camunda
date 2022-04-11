/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.service;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.engine.HistoricIdentityLinkLogDto;
import org.camunda.optimize.dto.optimize.importing.IdentityLinkLogEntryDto;
import org.camunda.optimize.dto.optimize.importing.IdentityLinkLogType;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.AssigneeCandidateGroupService;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.IdentityLinkLogImportJob;
import org.camunda.optimize.service.es.writer.usertask.IdentityLinkLogWriter;
import org.camunda.optimize.service.importing.engine.service.definition.ProcessDefinitionResolverService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class IdentityLinkLogImportService implements ImportService<HistoricIdentityLinkLogDto> {
  private static final Set<IdentityLinkLogType> SUPPORTED_TYPES = Set.of(
    IdentityLinkLogType.ASSIGNEE, IdentityLinkLogType.CANDIDATE
  );

  private final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private final EngineContext engineContext;
  private final IdentityLinkLogWriter identityLinkLogWriter;
  private final AssigneeCandidateGroupService assigneeCandidateGroupService;
  private final ProcessDefinitionResolverService processDefinitionResolverService;
  private final ConfigurationService configurationService;

  public IdentityLinkLogImportService(final ConfigurationService configurationService,
                                      final IdentityLinkLogWriter identityLinkLogWriter,
                                      final AssigneeCandidateGroupService assigneeCandidateGroupService,
                                      final EngineContext engineContext,
                                      final ProcessDefinitionResolverService processDefinitionResolverService) {
    this.elasticsearchImportJobExecutor = new ElasticsearchImportJobExecutor(
      getClass().getSimpleName(), configurationService
    );
    this.identityLinkLogWriter = identityLinkLogWriter;
    this.assigneeCandidateGroupService = assigneeCandidateGroupService;
    this.engineContext = engineContext;
    this.processDefinitionResolverService = processDefinitionResolverService;
    this.configurationService = configurationService;
  }

  @Override
  public void executeImport(final List<HistoricIdentityLinkLogDto> pageOfEngineEntities,
                            final Runnable importCompleteCallback) {
    log.trace("Importing identity link logs from engine...");

    final boolean newDataIsAvailable = !pageOfEngineEntities.isEmpty();
    if (newDataIsAvailable) {
      final List<IdentityLinkLogEntryDto> newOptimizeEntities =
        filterAndMapEngineEntitiesToOptimizeEntities(pageOfEngineEntities);
      final ElasticsearchImportJob<IdentityLinkLogEntryDto> elasticsearchImportJob =
        createElasticsearchImportJob(newOptimizeEntities, importCompleteCallback);
      addElasticsearchImportJobToQueue(elasticsearchImportJob);
    }
  }

  @Override
  public ElasticsearchImportJobExecutor getElasticsearchImportJobExecutor() {
    return elasticsearchImportJobExecutor;
  }

  private void addElasticsearchImportJobToQueue(final ElasticsearchImportJob<IdentityLinkLogEntryDto> elasticsearchImportJob) {
    elasticsearchImportJobExecutor.executeImportJob(elasticsearchImportJob);
  }

  private List<IdentityLinkLogEntryDto> filterAndMapEngineEntitiesToOptimizeEntities(
    final List<HistoricIdentityLinkLogDto> engineEntities) {
    return engineEntities.stream()
      .filter(instance -> instance.getProcessInstanceId() != null)
      .map(identityLinkLog -> processDefinitionResolverService.enrichEngineDtoWithDefinitionKey(
        engineContext,
        identityLinkLog,
        HistoricIdentityLinkLogDto::getProcessDefinitionKey,
        HistoricIdentityLinkLogDto::getProcessDefinitionId,
        HistoricIdentityLinkLogDto::setProcessDefinitionKey
      ))
      .filter(identityLinkLog -> identityLinkLog.getProcessDefinitionKey() != null)
      .map(this::mapEngineEntityToOptimizeEntity)
      .filter(entry -> SUPPORTED_TYPES.contains(entry.getType()))
      .collect(Collectors.toList());
  }

  private ElasticsearchImportJob<IdentityLinkLogEntryDto> createElasticsearchImportJob(
    final List<IdentityLinkLogEntryDto> identityLinkLogs,
    final Runnable callback) {
    final IdentityLinkLogImportJob importJob = new IdentityLinkLogImportJob(
      identityLinkLogWriter, assigneeCandidateGroupService, configurationService, callback
    );
    importJob.setEntitiesToImport(identityLinkLogs);
    return importJob;
  }

  private IdentityLinkLogEntryDto mapEngineEntityToOptimizeEntity(final HistoricIdentityLinkLogDto engineEntity) {
    return new IdentityLinkLogEntryDto(
      engineEntity.getId(),
      engineEntity.getProcessInstanceId(),
      engineEntity.getProcessDefinitionKey(),
      engineContext.getEngineAlias(),
      Optional.ofNullable(engineEntity.getType())
        .map(String::toUpperCase)
        .map(IdentityLinkLogType::valueOf)
        .orElse(null),
      engineEntity.getUserId(),
      engineEntity.getGroupId(),
      engineEntity.getTaskId(),
      engineEntity.getOperationType(),
      engineEntity.getAssignerId(),
      engineEntity.getTime()
    );
  }

}

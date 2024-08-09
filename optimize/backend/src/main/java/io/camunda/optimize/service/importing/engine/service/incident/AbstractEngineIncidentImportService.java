/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service.incident;

import io.camunda.optimize.dto.engine.HistoricIncidentEngineDto;
import io.camunda.optimize.dto.optimize.persistence.incident.IncidentDto;
import io.camunda.optimize.dto.optimize.persistence.incident.IncidentStatus;
import io.camunda.optimize.dto.optimize.persistence.incident.IncidentType;
import io.camunda.optimize.rest.engine.EngineContext;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import io.camunda.optimize.service.importing.DatabaseImportJobExecutor;
import io.camunda.optimize.service.importing.engine.service.ImportService;
import io.camunda.optimize.service.importing.engine.service.definition.ProcessDefinitionResolverService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractEngineIncidentImportService
    implements ImportService<HistoricIncidentEngineDto> {

  protected DatabaseImportJobExecutor databaseImportJobExecutor;
  protected EngineContext engineContext;
  protected final ConfigurationService configurationService;
  protected final DatabaseClient databaseClient;
  private final ProcessDefinitionResolverService processDefinitionResolverService;

  protected AbstractEngineIncidentImportService(
      final ConfigurationService configurationService,
      final EngineContext engineContext,
      final ProcessDefinitionResolverService processDefinitionResolverService,
      final DatabaseClient databaseClient) {
    databaseImportJobExecutor =
        new DatabaseImportJobExecutor(getClass().getSimpleName(), configurationService);
    this.engineContext = engineContext;
    this.processDefinitionResolverService = processDefinitionResolverService;
    this.configurationService = configurationService;
    this.databaseClient = databaseClient;
  }

  @Override
  public void executeImport(
      final List<HistoricIncidentEngineDto> pageOfEngineEntities,
      final Runnable importCompleteCallback) {
    log.trace("Importing incidents from engine...");

    final boolean newDataIsAvailable = !pageOfEngineEntities.isEmpty();
    if (newDataIsAvailable) {
      final List<IncidentDto> newOptimizeEntities =
          mapEngineEntitiesToOptimizeEntities(pageOfEngineEntities);
      final DatabaseImportJob<IncidentDto> databaseImportJob =
          createDatabaseImportJob(newOptimizeEntities, importCompleteCallback);
      addDatabaseImportJobToQueue(databaseImportJob);
    }
  }

  @Override
  public DatabaseImportJobExecutor getDatabaseImportJobExecutor() {
    return databaseImportJobExecutor;
  }

  private void addDatabaseImportJobToQueue(final DatabaseImportJob<?> databaseImportJob) {
    databaseImportJobExecutor.executeImportJob(databaseImportJob);
  }

  private List<IncidentDto> mapEngineEntitiesToOptimizeEntities(
      final List<HistoricIncidentEngineDto> engineIncidents) {
    logIncidentsToBeSkipped(engineIncidents);
    return engineIncidents.stream()
        .filter(this::containsProcessInstanceId)
        .map(this::mapEngineEntityToOptimizeEntity)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList());
  }

  protected abstract DatabaseImportJob<IncidentDto> createDatabaseImportJob(
      List<IncidentDto> incidents, Runnable callback);

  private void logIncidentsToBeSkipped(final List<HistoricIncidentEngineDto> engineIncidents) {
    final List<String> incidentIdsWithoutProcessInstanceId =
        engineIncidents.stream()
            .filter(incident -> !containsProcessInstanceId(incident))
            .map(HistoricIncidentEngineDto::getId)
            .collect(Collectors.toList());

    if (!incidentIdsWithoutProcessInstanceId.isEmpty()) {
      final String message =
          String.format(
              "Incidents with ID's %s aren't associated with any process instance ID and will be skipped. Usually, that "
                  + "can happen if history cleanup job or a timer start event job run out of retries.",
              incidentIdsWithoutProcessInstanceId);
      log.info(message);
    }
  }

  private boolean containsProcessInstanceId(final HistoricIncidentEngineDto incident) {
    return incident.getProcessInstanceId() != null;
  }

  private Optional<IncidentDto> mapEngineEntityToOptimizeEntity(
      final HistoricIncidentEngineDto engineEntity) {
    return processDefinitionResolverService
        .getDefinition(engineEntity.getProcessDefinitionId(), engineContext)
        .map(
            definition ->
                new IncidentDto(
                    engineEntity.getProcessInstanceId(),
                    definition.getKey(),
                    definition.getVersion(),
                    engineEntity
                        .getTenantId()
                        .orElseGet(() -> engineContext.getDefaultTenantId().orElse(null)),
                    engineContext.getEngineAlias(),
                    engineEntity.getId(),
                    engineEntity.getCreateTime(),
                    engineEntity.getEndTime(),
                    getDuration(engineEntity),
                    IncidentType.valueOfId(engineEntity.getIncidentType()),
                    engineEntity.getActivityId(),
                    engineEntity.getFailedActivityId(),
                    engineEntity.getIncidentMessage(),
                    extractIncidentStatus(engineEntity)));
  }

  private Long getDuration(final HistoricIncidentEngineDto engineEntity) {
    if (engineEntity.getCreateTime() != null && engineEntity.getEndTime() != null) {
      return ChronoUnit.MILLIS.between(engineEntity.getCreateTime(), engineEntity.getEndTime());
    }
    return null;
  }

  private IncidentStatus extractIncidentStatus(final HistoricIncidentEngineDto engineEntity) {
    if (engineEntity.isResolved()) {
      return IncidentStatus.RESOLVED;
    } else if (engineEntity.isOpen()) {
      return IncidentStatus.OPEN;
    } else if (engineEntity.isDeleted()) {
      return IncidentStatus.DELETED;
    } else {
      throw new OptimizeRuntimeException(
          "Incident with id [{}] that has been fetched from the engine has an invalid incident state. It's neither "
              + "resolved, open nor deleted.");
    }
  }
}

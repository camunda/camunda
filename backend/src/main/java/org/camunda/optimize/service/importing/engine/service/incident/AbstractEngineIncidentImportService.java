/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.service.incident;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.engine.HistoricIncidentEngineDto;
import org.camunda.optimize.dto.optimize.persistence.incident.IncidentDto;
import org.camunda.optimize.dto.optimize.persistence.incident.IncidentStatus;
import org.camunda.optimize.dto.optimize.persistence.incident.IncidentType;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.importing.engine.service.ImportService;
import org.camunda.optimize.service.importing.engine.service.definition.ProcessDefinitionResolverService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractEngineIncidentImportService implements ImportService<HistoricIncidentEngineDto> {

  protected ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  protected EngineContext engineContext;
  protected final ConfigurationService configurationService;
  private final ProcessDefinitionResolverService processDefinitionResolverService;

  protected AbstractEngineIncidentImportService(final ConfigurationService configurationService,
                                                final EngineContext engineContext,
                                                final ProcessDefinitionResolverService processDefinitionResolverService) {
    this.elasticsearchImportJobExecutor = new ElasticsearchImportJobExecutor(
      getClass().getSimpleName(), configurationService
    );
    this.engineContext = engineContext;
    this.processDefinitionResolverService = processDefinitionResolverService;
    this.configurationService = configurationService;
  }

  @Override
  public void executeImport(List<HistoricIncidentEngineDto> pageOfEngineEntities,
                            Runnable importCompleteCallback) {
    log.trace("Importing incidents from engine...");

    boolean newDataIsAvailable = !pageOfEngineEntities.isEmpty();
    if (newDataIsAvailable) {
      List<IncidentDto> newOptimizeEntities = mapEngineEntitiesToOptimizeEntities(pageOfEngineEntities);
      ElasticsearchImportJob<IncidentDto> elasticsearchImportJob =
        createElasticsearchImportJob(newOptimizeEntities, importCompleteCallback);
      addElasticsearchImportJobToQueue(elasticsearchImportJob);
    }
  }

  public ElasticsearchImportJobExecutor getElasticsearchImportJobExecutor() {
    return elasticsearchImportJobExecutor;
  }

  private void addElasticsearchImportJobToQueue(ElasticsearchImportJob<?> elasticsearchImportJob) {
    elasticsearchImportJobExecutor.executeImportJob(elasticsearchImportJob);
  }

  private List<IncidentDto> mapEngineEntitiesToOptimizeEntities(List<HistoricIncidentEngineDto> engineIncidents) {
    logIncidentsToBeSkipped(engineIncidents);
    return engineIncidents
      .stream()
      .filter(this::containsProcessInstanceId)
      .map(this::mapEngineEntityToOptimizeEntity)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .collect(Collectors.toList());
  }

  protected abstract ElasticsearchImportJob<IncidentDto> createElasticsearchImportJob(List<IncidentDto> incidents,
                                                                                      Runnable callback);

  private void logIncidentsToBeSkipped(List<HistoricIncidentEngineDto> engineIncidents) {
    List<String> incidentIdsWithoutProcessInstanceId = engineIncidents.stream()
      .filter(incident -> !containsProcessInstanceId(incident))
      .map(HistoricIncidentEngineDto::getId)
      .collect(Collectors.toList());

    if (!incidentIdsWithoutProcessInstanceId.isEmpty()) {
      final String message = String.format(
        "Incidents with ID's %s aren't associated with any process instance ID and will be skipped. Usually, that " +
          "can happen if history cleanup job or a timer start event job run out of retries.",
        incidentIdsWithoutProcessInstanceId
      );
      log.info(message);
    }
  }

  private boolean containsProcessInstanceId(final HistoricIncidentEngineDto incident) {
    return incident.getProcessInstanceId() != null;
  }

  private Optional<IncidentDto> mapEngineEntityToOptimizeEntity(final HistoricIncidentEngineDto engineEntity) {
    return processDefinitionResolverService.getDefinition(engineEntity.getProcessDefinitionId(), engineContext)
      .map(definition -> new IncidentDto(
        engineEntity.getProcessInstanceId(),
        definition.getKey(),
        definition.getVersion(),
        engineEntity.getTenantId().orElseGet(() -> engineContext.getDefaultTenantId().orElse(null)),
        engineContext.getEngineAlias(),
        engineEntity.getId(),
        engineEntity.getCreateTime(),
        engineEntity.getEndTime(),
        getDuration(engineEntity),
        IncidentType.valueOfId(engineEntity.getIncidentType()),
        engineEntity.getActivityId(),
        engineEntity.getFailedActivityId(),
        engineEntity.getIncidentMessage(),
        extractIncidentStatus(engineEntity)
      ));
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
        "Incident with id [{}] that has been fetched from the engine has an invalid incident state. It's neither " +
          "resolved, open nor deleted.");
    }
  }

}

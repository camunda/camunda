/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.ingested.service;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableUpdateDto;
import org.camunda.optimize.service.db.writer.variable.ProcessVariableUpdateWriter;
import org.camunda.optimize.service.importing.DatabaseImportJobExecutor;
import org.camunda.optimize.service.importing.DatabaseImportJob;
import org.camunda.optimize.service.importing.job.ExternalVariableUpdateDatabaseImportJob;
import org.camunda.optimize.service.importing.engine.service.ImportService;
import org.camunda.optimize.service.importing.engine.service.ObjectVariableService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;
import static org.camunda.optimize.service.db.schema.index.ExternalProcessVariableIndex.SERIALIZATION_DATA_FORMAT;

@Slf4j
public class ExternalVariableUpdateImportService implements ImportService<ExternalProcessVariableDto> {

  public static final long DEFAULT_VERSION = 1000L;

  private final DatabaseImportJobExecutor databaseImportJobExecutor;
  private final ProcessVariableUpdateWriter variableWriter;
  private final ConfigurationService configurationService;
  private final ObjectVariableService objectVariableService;

  public ExternalVariableUpdateImportService(final ConfigurationService configurationService,
                                             final ProcessVariableUpdateWriter variableWriter,
                                             final ObjectVariableService objectVariableService) {
    this.databaseImportJobExecutor = new DatabaseImportJobExecutor(
      getClass().getSimpleName(), configurationService
    );
    this.variableWriter = variableWriter;
    this.configurationService = configurationService;
    this.objectVariableService = objectVariableService;
  }

  @Override
  public void executeImport(final List<ExternalProcessVariableDto> pageOfExternalEntities,
                            final Runnable importCompleteCallback) {
    log.trace("Importing external variable entities...");

    boolean newDataIsAvailable = !pageOfExternalEntities.isEmpty();
    if (newDataIsAvailable) {
      List<ProcessVariableDto> newOptimizeEntities =
        mapExternalEntitiesToOptimizeEntities(pageOfExternalEntities);
      DatabaseImportJob<ProcessVariableDto> databaseImportJob = createDatabaseImportJob(
        newOptimizeEntities,
        importCompleteCallback
      );
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

  private List<ProcessVariableDto> mapExternalEntitiesToOptimizeEntities(final List<ExternalProcessVariableDto> externalEntities) {
    final List<ExternalProcessVariableDto> deduplicatedVariables =
      resolveDuplicateVariableUpdatesPerProcessInstance(externalEntities);
    List<ProcessVariableUpdateDto> processVariables = deduplicatedVariables.stream()
      .map(this::convertExternalToProcessVariableDto)
      .toList();
    return objectVariableService.convertToProcessVariableDtos(processVariables);
  }

  private List<ExternalProcessVariableDto> resolveDuplicateVariableUpdatesPerProcessInstance(
    final List<ExternalProcessVariableDto> externalEntities) {
    // if we have more than one variable update for the same variable within one process instance, we only import the
    // variable with the latest ingestion timestamp
    List<ExternalProcessVariableDto> deduplicatedVariables = new ArrayList<>();
    Map<String, List<ExternalProcessVariableDto>> variablesByProcessInstanceId = new HashMap<>();
    for (ExternalProcessVariableDto variable : externalEntities) {
      variablesByProcessInstanceId.putIfAbsent(variable.getProcessInstanceId(), new ArrayList<>());
      variablesByProcessInstanceId.get(variable.getProcessInstanceId()).add(variable);
    }
    variablesByProcessInstanceId
      .forEach((id, vars) -> deduplicatedVariables.addAll(resolveDuplicateVariableUpdates(vars)));
    return deduplicatedVariables;
  }

  private Set<ExternalProcessVariableDto> resolveDuplicateVariableUpdates(final List<ExternalProcessVariableDto> externalEntities) {
    return new HashSet<>(
      externalEntities.stream().collect(toMap(
        ExternalProcessVariableDto::getVariableId,
        Function.identity(),
        (var1, var2) ->
          // if there is more than one update for the same variable, the update with the latest
          // ingestion timestamp wins
          var1.getIngestionTimestamp().compareTo(var2.getIngestionTimestamp()) > 0 ? var1 : var2
      )).values());
  }

  private ProcessVariableUpdateDto convertExternalToProcessVariableDto(final ExternalProcessVariableDto externalVariable) {
    final Map<String, Object> valueInfo = new HashMap<>();
    valueInfo.put(SERIALIZATION_DATA_FORMAT, externalVariable.getSerializationDataFormat());
    return (ProcessVariableUpdateDto) new ProcessVariableUpdateDto()
      .setId(externalVariable.getVariableId())
      .setName(externalVariable.getVariableName())
      .setType(externalVariable.getVariableType().getId())
      .setValueInfo(valueInfo)
      .setValue(externalVariable.getVariableValue())
      .setProcessDefinitionKey(externalVariable.getProcessDefinitionKey())
      .setProcessInstanceId(externalVariable.getProcessInstanceId())
      // defaulting to the same version as there is no versioning for external variables
      .setVersion(DEFAULT_VERSION)
      .setTimestamp(OffsetDateTime.ofInstant(
        Instant.ofEpochMilli(externalVariable.getIngestionTimestamp()),
        ZoneId.systemDefault()
      ));
  }

  private DatabaseImportJob<ProcessVariableDto> createDatabaseImportJob(final List<ProcessVariableDto> processVariables,
                                                                             final Runnable callback) {
    final ExternalVariableUpdateDatabaseImportJob importJob = new ExternalVariableUpdateDatabaseImportJob(
      variableWriter, configurationService, callback
    );
    importJob.setEntitiesToImport(processVariables);
    return importJob;
  }

}

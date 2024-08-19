/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.ingested.service;

import static io.camunda.optimize.service.db.schema.index.ExternalProcessVariableIndex.SERIALIZATION_DATA_FORMAT;
import static java.util.stream.Collectors.toMap;

import io.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableUpdateDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.variable.ProcessVariableUpdateWriter;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import io.camunda.optimize.service.importing.DatabaseImportJobExecutor;
import io.camunda.optimize.service.importing.engine.service.ImportService;
import io.camunda.optimize.service.importing.engine.service.ObjectVariableService;
import io.camunda.optimize.service.importing.job.ExternalVariableUpdateDatabaseImportJob;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
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
import org.slf4j.Logger;

public class ExternalVariableUpdateImportService
    implements ImportService<ExternalProcessVariableDto> {

  public static final long DEFAULT_VERSION = 1000L;
  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(ExternalVariableUpdateImportService.class);

  private final DatabaseImportJobExecutor databaseImportJobExecutor;
  private final ProcessVariableUpdateWriter variableWriter;
  private final ConfigurationService configurationService;
  private final ObjectVariableService objectVariableService;
  private final DatabaseClient databaseClient;

  public ExternalVariableUpdateImportService(
      final ConfigurationService configurationService,
      final ProcessVariableUpdateWriter variableWriter,
      final ObjectVariableService objectVariableService,
      final DatabaseClient databaseClient) {
    databaseImportJobExecutor =
        new DatabaseImportJobExecutor(getClass().getSimpleName(), configurationService);
    this.variableWriter = variableWriter;
    this.configurationService = configurationService;
    this.objectVariableService = objectVariableService;
    this.databaseClient = databaseClient;
  }

  @Override
  public void executeImport(
      final List<ExternalProcessVariableDto> pageOfExternalEntities,
      final Runnable importCompleteCallback) {
    log.trace("Importing external variable entities...");

    final boolean newDataIsAvailable = !pageOfExternalEntities.isEmpty();
    if (newDataIsAvailable) {
      final List<ProcessVariableDto> newOptimizeEntities =
          mapExternalEntitiesToOptimizeEntities(pageOfExternalEntities);
      final DatabaseImportJob<ProcessVariableDto> databaseImportJob =
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

  private List<ProcessVariableDto> mapExternalEntitiesToOptimizeEntities(
      final List<ExternalProcessVariableDto> externalEntities) {
    final List<ExternalProcessVariableDto> deduplicatedVariables =
        resolveDuplicateVariableUpdatesPerProcessInstance(externalEntities);
    final List<ProcessVariableUpdateDto> processVariables =
        deduplicatedVariables.stream().map(this::convertExternalToProcessVariableDto).toList();
    return objectVariableService.convertToProcessVariableDtos(processVariables);
  }

  private List<ExternalProcessVariableDto> resolveDuplicateVariableUpdatesPerProcessInstance(
      final List<ExternalProcessVariableDto> externalEntities) {
    // if we have more than one variable update for the same variable within one process instance,
    // we only import the
    // variable with the latest ingestion timestamp
    final List<ExternalProcessVariableDto> deduplicatedVariables = new ArrayList<>();
    final Map<String, List<ExternalProcessVariableDto>> variablesByProcessInstanceId =
        new HashMap<>();
    for (final ExternalProcessVariableDto variable : externalEntities) {
      variablesByProcessInstanceId.putIfAbsent(variable.getProcessInstanceId(), new ArrayList<>());
      variablesByProcessInstanceId.get(variable.getProcessInstanceId()).add(variable);
    }
    variablesByProcessInstanceId.forEach(
        (id, vars) -> deduplicatedVariables.addAll(resolveDuplicateVariableUpdates(vars)));
    return deduplicatedVariables;
  }

  private Set<ExternalProcessVariableDto> resolveDuplicateVariableUpdates(
      final List<ExternalProcessVariableDto> externalEntities) {
    return new HashSet<>(
        externalEntities.stream()
            .collect(
                toMap(
                    ExternalProcessVariableDto::getVariableId,
                    Function.identity(),
                    (var1, var2) ->
                        // if there is more than one update for the same variable, the update with
                        // the latest
                        // ingestion timestamp wins
                        var1.getIngestionTimestamp().compareTo(var2.getIngestionTimestamp()) > 0
                            ? var1
                            : var2))
            .values());
  }

  private ProcessVariableUpdateDto convertExternalToProcessVariableDto(
      final ExternalProcessVariableDto externalVariable) {
    final Map<String, Object> valueInfo = new HashMap<>();
    valueInfo.put(SERIALIZATION_DATA_FORMAT, externalVariable.getSerializationDataFormat());
    final ProcessVariableUpdateDto processVariableUpdateDto = new ProcessVariableUpdateDto();
    processVariableUpdateDto.setId(externalVariable.getVariableId());
    processVariableUpdateDto.setName(externalVariable.getVariableName());
    processVariableUpdateDto.setType(externalVariable.getVariableType().getId());
    processVariableUpdateDto.setValue(externalVariable.getVariableValue());
    processVariableUpdateDto.setTimestamp(
        OffsetDateTime.ofInstant(
            Instant.ofEpochMilli(externalVariable.getIngestionTimestamp()),
            ZoneId.systemDefault()));
    processVariableUpdateDto.setValueInfo(valueInfo);
    processVariableUpdateDto.setProcessDefinitionKey(externalVariable.getProcessDefinitionKey());
    processVariableUpdateDto.setProcessInstanceId(externalVariable.getProcessInstanceId());
    processVariableUpdateDto.setVersion(DEFAULT_VERSION);
    return processVariableUpdateDto;
  }

  private DatabaseImportJob<ProcessVariableDto> createDatabaseImportJob(
      final List<ProcessVariableDto> processVariables, final Runnable callback) {
    final ExternalVariableUpdateDatabaseImportJob importJob =
        new ExternalVariableUpdateDatabaseImportJob(
            variableWriter, configurationService, callback, databaseClient);
    importJob.setEntitiesToImport(processVariables);
    return importJob;
  }
}

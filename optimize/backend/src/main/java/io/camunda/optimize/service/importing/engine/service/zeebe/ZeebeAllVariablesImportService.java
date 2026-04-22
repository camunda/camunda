/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service.zeebe;

import io.camunda.optimize.dto.optimize.importing.AllVariablesDto;
import io.camunda.optimize.dto.zeebe.variable.ZeebeVariableDataDto;
import io.camunda.optimize.dto.zeebe.variable.ZeebeVariableRecordDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.AllVariablesWriter;
import io.camunda.optimize.service.importing.DatabaseImportJobExecutor;
import io.camunda.optimize.service.importing.engine.service.ImportService;
import io.camunda.optimize.service.importing.job.AllVariablesDatabaseImportJob;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;

/**
 * Imports all Zeebe variable records (regardless of name) into {@code optimize-all-variables}.
 * Each document corresponds to one variable record, keyed by variable key. Used to evaluate the
 * performance impact of a dedicated variables index.
 */
public class ZeebeAllVariablesImportService implements ImportService<ZeebeVariableRecordDto> {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ZeebeAllVariablesImportService.class);

  private static final Set<VariableIntent> INTENTS_TO_IMPORT =
      Set.of(VariableIntent.CREATED, VariableIntent.UPDATED);

  private final DatabaseImportJobExecutor databaseImportJobExecutor;
  private final AllVariablesWriter allVariablesWriter;
  private final DatabaseClient databaseClient;
  private final ConfigurationService configurationService;

  public ZeebeAllVariablesImportService(
      final ConfigurationService configurationService,
      final AllVariablesWriter allVariablesWriter,
      final DatabaseClient databaseClient) {
    this.configurationService = configurationService;
    this.allVariablesWriter = allVariablesWriter;
    this.databaseClient = databaseClient;
    databaseImportJobExecutor =
        new DatabaseImportJobExecutor(getClass().getSimpleName(), configurationService);
  }

  @Override
  public void executeImport(
      final List<ZeebeVariableRecordDto> zeebeRecords, final Runnable importCompleteCallback) {
    if (zeebeRecords.isEmpty()) {
      importCompleteCallback.run();
      return;
    }

    final List<AllVariablesDto> documents = mapToAllVariablesDtos(zeebeRecords);

    if (!documents.isEmpty()) {
      final AllVariablesDatabaseImportJob importJob =
          new AllVariablesDatabaseImportJob(
              allVariablesWriter, configurationService, importCompleteCallback, databaseClient);
      importJob.setEntitiesToImport(documents);
      databaseImportJobExecutor.executeImportJob(importJob);
    } else {
      importCompleteCallback.run();
    }
  }

  @Override
  public DatabaseImportJobExecutor getDatabaseImportJobExecutor() {
    return databaseImportJobExecutor;
  }

  private List<AllVariablesDto> mapToAllVariablesDtos(
      final List<ZeebeVariableRecordDto> zeebeRecords) {
    final List<AllVariablesDto> result =
        zeebeRecords.stream()
            .filter(r -> INTENTS_TO_IMPORT.contains(r.getIntent()))
            .map(this::toDto)
            .toList();

    LOG.debug(
        "Processing {} fetched Zeebe variable records: {} mapped to all-variables documents.",
        zeebeRecords.size(),
        result.size());

    return result;
  }

  private AllVariablesDto toDto(final ZeebeVariableRecordDto record) {
    final ZeebeVariableDataDto v = record.getValue();
    final AllVariablesDto dto = new AllVariablesDto();
    // record.getKey() is the variable key in the Zeebe protocol
    dto.setVariableKey(String.valueOf(record.getKey()));
    dto.setProcessInstanceKey(String.valueOf(v.getProcessInstanceKey()));
    dto.setProcessDefinitionKey(String.valueOf(v.getProcessDefinitionKey()));
    dto.setScopeKey(String.valueOf(v.getScopeKey()));
    dto.setTenantId(v.getTenantId());
    dto.setName(v.getName());
    dto.setValue(v.getValue());
    dto.setTimestamp(record.getTimestamp());
    dto.setPartitionId(record.getPartitionId());
    dto.setSequence(record.getSequence());
    return dto;
  }
}

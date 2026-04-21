/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service.zeebe;

import io.camunda.optimize.dto.optimize.importing.ReportingMetricsDto;
import io.camunda.optimize.dto.optimize.importing.ReportingMetricsMappings;
import io.camunda.optimize.dto.zeebe.variable.ZeebeVariableDataDto;
import io.camunda.optimize.dto.zeebe.variable.ZeebeVariableRecordDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.ReportingMetricsWriter;
import io.camunda.optimize.service.importing.DatabaseImportJobExecutor;
import io.camunda.optimize.service.importing.engine.service.ImportService;
import io.camunda.optimize.service.importing.job.ReportingMetricsDatabaseImportJob;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;

/**
 * Filters Zeebe variable records for the {@code REPORTING_PROCESS_*} prefix, groups them by process
 * instance key, and upserts aggregated documents into {@code optimize-reporting-metrics}.
 */
public class ZeebeReportingMetricsImportService implements ImportService<ZeebeVariableRecordDto> {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ZeebeReportingMetricsImportService.class);

  private static final Set<VariableIntent> INTENTS_TO_IMPORT =
      Set.of(VariableIntent.CREATED, VariableIntent.UPDATED);

  private final DatabaseImportJobExecutor databaseImportJobExecutor;
  private final ReportingMetricsWriter reportingMetricsWriter;
  private final DatabaseClient databaseClient;
  private final ConfigurationService configurationService;

  public ZeebeReportingMetricsImportService(
      final ConfigurationService configurationService,
      final ReportingMetricsWriter reportingMetricsWriter,
      final DatabaseClient databaseClient) {
    this.configurationService = configurationService;
    this.reportingMetricsWriter = reportingMetricsWriter;
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

    final List<ReportingMetricsDto> metricsDocuments = filterAndMapToReportingMetrics(zeebeRecords);

    if (!metricsDocuments.isEmpty()) {
      final ReportingMetricsDatabaseImportJob importJob =
          new ReportingMetricsDatabaseImportJob(
              reportingMetricsWriter, configurationService, importCompleteCallback, databaseClient);
      importJob.setEntitiesToImport(metricsDocuments);
      databaseImportJobExecutor.executeImportJob(importJob);
    } else {
      importCompleteCallback.run();
    }
  }

  @Override
  public DatabaseImportJobExecutor getDatabaseImportJobExecutor() {
    return databaseImportJobExecutor;
  }

  private List<ReportingMetricsDto> filterAndMapToReportingMetrics(
      final List<ZeebeVariableRecordDto> zeebeRecords) {
    final Map<Long, List<ZeebeVariableRecordDto>> byProcessInstance =
        zeebeRecords.stream()
            .filter(r -> INTENTS_TO_IMPORT.contains(r.getIntent()))
            .filter(
                r ->
                    r.getValue().getName() != null
                        && r.getValue()
                            .getName()
                            .startsWith(ReportingMetricsMappings.REPORTING_PREFIX))
            .collect(Collectors.groupingBy(r -> r.getValue().getProcessInstanceKey()));

    final List<ReportingMetricsDto> result =
        byProcessInstance.values().stream().map(this::buildMetricsDoc).toList();

    LOG.debug(
        "Processing {} fetched Zeebe variable records: {} REPORTING_PROCESS_* records for {} process instances.",
        zeebeRecords.size(),
        byProcessInstance.values().stream().mapToLong(List::size).sum(),
        result.size());

    return result;
  }

  private ReportingMetricsDto buildMetricsDoc(final List<ZeebeVariableRecordDto> records) {
    final ReportingMetricsDto doc = new ReportingMetricsDto();

    // Identity fields from the first record
    final ZeebeVariableDataDto first = records.getFirst().getValue();
    doc.setProcessInstanceKey(String.valueOf(first.getProcessInstanceKey()));
    doc.setProcessDefinitionKey(String.valueOf(first.getProcessDefinitionKey()));
    doc.setTenantId(first.getTenantId());

    // Timestamp bounds across all records in this batch
    for (final ZeebeVariableRecordDto record : records) {
      final long ts = record.getTimestamp();
      if (doc.getFirstSeenAt() == null || ts < doc.getFirstSeenAt()) {
        doc.setFirstSeenAt(ts);
      }
      if (doc.getLastSeenAt() == null || ts > doc.getLastSeenAt()) {
        doc.setLastSeenAt(ts);
      }

      // Map variable name → DTO field
      final String varName = record.getValue().getName();
      final String rawValue = record.getValue().getValue();
      if (varName != null && rawValue != null) {
        if (!ReportingMetricsMappings.applyVariable(doc, varName, rawValue)) {
          LOG.trace("Ignoring unknown REPORTING_PROCESS_ variable: {}", varName);
        }
      }
    }

    return doc;
  }
}

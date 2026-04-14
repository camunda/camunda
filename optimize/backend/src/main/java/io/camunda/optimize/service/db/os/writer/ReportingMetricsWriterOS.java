/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.db.DatabaseConstants.REPORTING_METRICS_INDEX_NAME;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.RequestType;
import io.camunda.optimize.dto.optimize.importing.ReportingMetricsDto;
import io.camunda.optimize.service.db.writer.ReportingMetricsWriter;
import io.camunda.optimize.service.db.writer.ReportingMetricsWriterSupport;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ReportingMetricsWriterOS implements ReportingMetricsWriter {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ReportingMetricsWriterOS.class);

  private final ObjectMapper objectMapper;

  public ReportingMetricsWriterOS(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public List<ImportRequestDto> generateImports(final List<ReportingMetricsDto> metricsDocuments) {
    LOG.debug("Creating reporting-metrics imports for {} documents.", metricsDocuments.size());
    return metricsDocuments.stream()
        .map(
            doc ->
                ImportRequestDto.builder()
                    .importName("reporting metrics")
                    .type(RequestType.UPDATE)
                    .id(doc.getProcessInstanceKey())
                    .indexName(REPORTING_METRICS_INDEX_NAME)
                    .source(doc)
                    .scriptData(ReportingMetricsWriterSupport.buildScriptData(doc, objectMapper))
                    .retryNumberOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
                    .build())
        .collect(Collectors.toList());
  }
}

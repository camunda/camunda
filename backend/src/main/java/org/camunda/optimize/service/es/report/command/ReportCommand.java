/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.Range;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.DefinitionBasedType;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;

import static org.camunda.optimize.service.util.DefinitionQueryUtil.createDefinitionQuery;

public abstract class ReportCommand<R extends ReportEvaluationResult, RD extends ReportDefinitionDto<?>>
  implements Command<RD> {

  protected Logger logger = LoggerFactory.getLogger(getClass());

  protected RD reportDefinition;
  protected OptimizeElasticsearchClient esClient;
  protected ConfigurationService configurationService;
  protected ObjectMapper objectMapper;
  protected Range<OffsetDateTime> dateIntervalRange;
  protected Integer recordLimit;

  @Override
  public R evaluate(final CommandContext<RD> commandContext) throws OptimizeException {
    reportDefinition = commandContext.getReportDefinition();
    esClient = commandContext.getEsClient();
    configurationService = commandContext.getConfigurationService();
    objectMapper = commandContext.getObjectMapper();
    dateIntervalRange = commandContext.getDateIntervalRange();
    recordLimit = commandContext.getRecordLimit();

    beforeEvaluate(commandContext);

    final R evaluationResult = evaluate();
    final R filteredResultData = filterResultData(commandContext, evaluationResult);
    final R enrichedResultData = enrichResultData(commandContext, filteredResultData);
    sortResultData(enrichedResultData);
    return enrichedResultData;
  }

  protected abstract void beforeEvaluate(final CommandContext<RD> commandContext);

  protected abstract R evaluate() throws OptimizeException;

  protected abstract void sortResultData(R evaluationResult);

  protected abstract String getLatestDefinitionVersionToKey(String definitionKey);

  protected R filterResultData(final CommandContext<RD> commandContext, R evaluationResult) {
    return evaluationResult;
  }

  protected R enrichResultData(final CommandContext<RD> commandContext, final R evaluationResult) {
    return evaluationResult;
  }

  public RD getReportDefinition() {
    return reportDefinition;
  }

  @SuppressWarnings("unchecked")
  public <T extends ReportDataDto> T getReportData() {
    return (T) reportDefinition.getData();
  }

  protected BoolQueryBuilder setupBaseQuery(final SingleReportDataDto reportData, final DefinitionBasedType type) {
    return createDefinitionQuery(
      reportData.getDefinitionKey(),
      reportData.getDefinitionVersions(),
      reportData.getTenantIds(),
      type,
      this::getLatestDefinitionVersionToKey
    );
  }
}

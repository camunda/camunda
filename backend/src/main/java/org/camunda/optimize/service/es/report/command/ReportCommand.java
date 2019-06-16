/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.Range;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.service.es.report.result.ReportEvaluationResult;
import org.camunda.optimize.service.es.schema.type.DefinitionBasedType;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

public abstract class ReportCommand<R extends ReportEvaluationResult, RD extends ReportDefinitionDto<?>>
  implements Command<RD> {

  protected Logger logger = LoggerFactory.getLogger(getClass());

  protected RD reportDefinition;
  protected RestHighLevelClient esClient;
  protected ConfigurationService configurationService;
  protected ObjectMapper objectMapper;
  protected Range<OffsetDateTime> dateIntervalRange;

  @Override
  public R evaluate(final CommandContext<RD> commandContext) throws OptimizeException {
    reportDefinition = commandContext.getReportDefinition();
    esClient = commandContext.getEsClient();
    configurationService = commandContext.getConfigurationService();
    objectMapper = commandContext.getObjectMapper();
    dateIntervalRange = commandContext.getDateIntervalRange();
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
    final String definitionKey = reportData.getDefinitionKey();
    final String definitionVersion = reportData.getDefinitionVersion();
    final BoolQueryBuilder query = boolQuery();
    query.must(createTenantIdQuery(reportData, type.getTenantIdFieldName()));
    query.must(termQuery(type.getDefinitionKeyFieldName(), definitionKey));
    if (!ReportConstants.ALL_VERSIONS.equalsIgnoreCase(definitionVersion)) {
      query.must(termQuery(type.getDefinitionVersionFieldName(), definitionVersion));
    }
    return query;
  }

  public static QueryBuilder createTenantIdQuery(final SingleReportDataDto reportData, final String tenantField) {
    final List<String> tenantIds = reportData.getTenantIds();
    return createTenantIdQuery(tenantField, tenantIds);
  }

  public static QueryBuilder createTenantIdQuery(final String tenantField, final List<String> tenantIds) {
    final AtomicBoolean includeNotDefinedTenant = new AtomicBoolean(false);
    final List<String> tenantIdTerms = tenantIds.stream()
      .peek(id -> {
        if (id == null) {
          includeNotDefinedTenant.set(true);
        }
      })
      .filter(Objects::nonNull)
      .collect(Collectors.toList());

    final BoolQueryBuilder tenantQueryBuilder = boolQuery().minimumShouldMatch(1);
    if (!tenantIdTerms.isEmpty()) {
      tenantQueryBuilder.should(termsQuery(tenantField, tenantIdTerms));
    }
    if (includeNotDefinedTenant.get()) {
      tenantQueryBuilder.should(boolQuery().mustNot(existsQuery(tenantField)));
    }
    if (tenantIdTerms.isEmpty() && !includeNotDefinedTenant.get()){
      // All tenants have been deselected and therefore we should not return any data.
      // This query ensures that the condition never holds for any data.
      tenantQueryBuilder.mustNot(matchAllQuery());
    }

    return tenantQueryBuilder;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.view.decision;

import static io.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;
import static io.camunda.optimize.service.db.es.report.interpreter.util.SortUtilsES.getSortOrder;
import static io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex.INPUTS;
import static io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex.OUTPUTS;
import static io.camunda.optimize.service.util.DecisionVariableHelper.getVariableClauseIdField;
import static io.camunda.optimize.service.util.DecisionVariableHelper.getVariableMultivalueFields;
import static io.camunda.optimize.service.util.DecisionVariableHelper.getVariableValueFieldForType;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.mapping.FieldType;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import io.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import io.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.reader.ElasticsearchReaderUtil;
import io.camunda.optimize.service.db.reader.DecisionVariableReader;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.view.decision.AbstractDecisionViewRawDataInterpreter;
import io.camunda.optimize.service.db.report.plan.decision.DecisionExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class DecisionViewRawDataInterpreterES extends AbstractDecisionViewRawDataInterpreter
    implements DecisionViewInterpreterES {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(DecisionViewRawDataInterpreterES.class);
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;
  private final OptimizeElasticsearchClient esClient;
  private final DecisionVariableReader decisionVariableReader;

  public DecisionViewRawDataInterpreterES(
      final ConfigurationService configurationService,
      final @Qualifier("optimizeObjectMapper") ObjectMapper objectMapper,
      final OptimizeElasticsearchClient esClient,
      final DecisionVariableReader decisionVariableReader) {
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
    this.esClient = esClient;
    this.decisionVariableReader = decisionVariableReader;
  }

  @Override
  public void adjustSearchRequest(
      final SearchRequest.Builder searchRequestBuilder,
      final BoolQuery.Builder baseQueryBuilder,
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    searchRequestBuilder.source(s -> s.fetch(true));
    context
        .getPagination()
        .ifPresent(
            pag -> {
              if (context.isCsvExport()) {
                searchRequestBuilder.size(
                    pag.getLimit() > MAX_RESPONSE_SIZE_LIMIT
                        ? MAX_RESPONSE_SIZE_LIMIT
                        : pag.getLimit());
                searchRequestBuilder.scroll(
                    s ->
                        s.time(
                            configurationService
                                    .getElasticSearchConfiguration()
                                    .getScrollTimeoutInSeconds()
                                + "s"));
              } else {
                if (pag.getLimit() > MAX_RESPONSE_SIZE_LIMIT) {
                  pag.setLimit(MAX_RESPONSE_SIZE_LIMIT);
                }
                searchRequestBuilder.size(pag.getLimit()).from(pag.getOffset());
              }
            });

    addSortingToQuery(context.getReportData(), searchRequestBuilder);
  }

  @Override
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregations(
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    return Map.of();
  }

  @Override
  public CompositeCommandResult.ViewResult retrieveResult(
      final ResponseBody<?> response,
      final Map<String, Aggregate> aggs,
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    final List<DecisionInstanceDto> rawDataDecisionInstanceDtos;
    if (context.isCsvExport()) {
      rawDataDecisionInstanceDtos =
          ElasticsearchReaderUtil.retrieveScrollResultsTillLimit(
              response,
              DecisionInstanceDto.class,
              objectMapper,
              esClient,
              configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds(),
              context.getPagination().orElse(new PaginationDto()).getLimit(),
              true);
    } else {
      rawDataDecisionInstanceDtos =
          ElasticsearchReaderUtil.mapHits(
              response.hits(), DecisionInstanceDto.class, objectMapper, true);
    }
    final List<RawDataDecisionInstanceDto> rawData =
        rawDataSingleReportResultDtoMapper.mapFrom(
            rawDataDecisionInstanceDtos,
            getInputVariableEntries(context.getReportData()),
            getOutputVars(context.getReportData()));
    addNewVariablesAndDtoFieldsToTableColumnConfig(context, rawData);
    return CompositeCommandResult.ViewResult.builder().rawData(rawData).build();
  }

  @Override
  public CompositeCommandResult.ViewResult createEmptyResult(
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    return CompositeCommandResult.ViewResult.builder().rawData(new ArrayList<>()).build();
  }

  private void addSortingToQuery(
      final DecisionReportDataDto decisionReportData,
      final SearchRequest.Builder searchRequestBuilder) {
    final Optional<ReportSortingDto> customSorting =
        decisionReportData.getConfiguration().getSorting();
    final String sortByField =
        customSorting
            .flatMap(ReportSortingDto::getBy)
            .orElse(DecisionInstanceIndex.EVALUATION_DATE_TIME);
    final SortOrder sortOrder =
        customSorting
            .flatMap(ReportSortingDto::getOrder)
            .map(order -> SortOrder.valueOf(order.name()))
            .orElse(SortOrder.DESC);

    if (sortByField.startsWith(INPUT_VARIABLE_PREFIX)) {
      addSortByInputVariable(searchRequestBuilder, sortByField, sortOrder);
    } else if (sortByField.startsWith(OUTPUT_VARIABLE_PREFIX)) {
      addSortByOutputVariable(searchRequestBuilder, sortByField, sortOrder);
    } else {
      searchRequestBuilder.sort(
          s ->
              s.field(
                  f ->
                      f.field(sortByField)
                          .order(getSortOrder(sortOrder))
                          // this ensures the query doesn't fail on unknown properties but just
                          // ignores them this is done to ensure consistent behavior compared to
                          // unknown variable names as ES doesn't fail there
                          // https://www.elastic.co/guide/en/elasticsearch/reference/6.0/search-request-sort.html#_ignoring_unmapped_fields
                          .unmappedType(FieldType.Short)));
    }
  }

  private void addSortByInputVariable(
      final SearchRequest.Builder searchRequestBuilder,
      final String sortByField,
      final SortOrder sortOrder) {
    getVariableMultivalueFields()
        .forEach(
            type ->
                searchRequestBuilder.sort(
                    createSortByVariable(
                        sortByField, sortOrder, INPUT_VARIABLE_PREFIX, INPUTS, type)));

    // add default string field as last as it will always be present
    searchRequestBuilder.sort(
        createSortByVariable(
            sortByField, sortOrder, INPUT_VARIABLE_PREFIX, INPUTS, VariableType.STRING));
  }

  private void addSortByOutputVariable(
      final SearchRequest.Builder searchRequestBuilder,
      final String sortByField,
      final SortOrder sortOrder) {
    getVariableMultivalueFields()
        .forEach(
            type ->
                searchRequestBuilder.sort(
                    createSortByVariable(
                        sortByField, sortOrder, OUTPUT_VARIABLE_PREFIX, OUTPUTS, type)));

    // add default string field as last as it will always be present
    searchRequestBuilder.sort(
        createSortByVariable(
            sortByField, sortOrder, OUTPUT_VARIABLE_PREFIX, OUTPUTS, VariableType.STRING));
  }

  private SortOptions createSortByVariable(
      final String sortByField,
      final SortOrder sortOrder,
      final String prefix,
      final String variablePath,
      final VariableType type) {
    final String inputVariableId = sortByField.substring(prefix.length());
    final String variableValuePath = getVariableValueFieldForType(variablePath, type);
    final String variableIdPath = getVariableClauseIdField(variablePath);

    return SortOptions.of(
        s ->
            s.field(
                f ->
                    f.field(variableValuePath)
                        .nested(
                            n ->
                                n.path(variablePath)
                                    .filter(
                                        ff ->
                                            ff.term(
                                                t ->
                                                    t.field(variableIdPath)
                                                        .value(inputVariableId))))
                        .order(getSortOrder(sortOrder))));
  }

  public DecisionVariableReader getDecisionVariableReader() {
    return this.decisionVariableReader;
  }
}

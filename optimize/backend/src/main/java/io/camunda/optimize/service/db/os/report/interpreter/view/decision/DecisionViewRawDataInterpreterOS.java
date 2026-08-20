/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.view.decision;

import static io.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.term;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.transformSortOrder;
import static io.camunda.optimize.service.db.os.client.dsl.UnitDSL.seconds;
import static io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex.INPUTS;
import static io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex.OUTPUTS;
import static io.camunda.optimize.service.exceptions.ExceptionHelper.safe;
import static io.camunda.optimize.service.util.DecisionVariableHelper.getVariableClauseIdField;
import static io.camunda.optimize.service.util.DecisionVariableHelper.getVariableMultivalueFields;
import static io.camunda.optimize.service.util.DecisionVariableHelper.getVariableValueFieldForType;
import static java.lang.Math.min;
import static java.lang.String.format;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import io.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.reader.DecisionVariableReader;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.view.decision.AbstractDecisionViewRawDataInterpreter;
import io.camunda.optimize.service.db.report.plan.decision.DecisionExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewResult;
import io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.opensearch.client.opensearch._types.FieldSort;
import org.opensearch.client.opensearch._types.NestedSortValue;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class DecisionViewRawDataInterpreterOS extends AbstractDecisionViewRawDataInterpreter
    implements DecisionViewInterpreterOS {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(DecisionViewRawDataInterpreterOS.class);
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;
  private final OptimizeOpenSearchClient osClient;
  private final DecisionVariableReader decisionVariableReader;

  public DecisionViewRawDataInterpreterOS(
      final ConfigurationService configurationService,
      final ObjectMapper objectMapper,
      final OptimizeOpenSearchClient osClient,
      final DecisionVariableReader decisionVariableReader) {
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
    this.osClient = osClient;
    this.decisionVariableReader = decisionVariableReader;
  }

  @Override
  public void adjustSearchRequest(
      final SearchRequest.Builder searchRequestBuilder,
      final Query baseQuery,
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    searchRequestBuilder.source(b -> b.fetch(true));
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
                    seconds(
                        configurationService
                            .getOpenSearchConfiguration()
                            .getScrollTimeoutInSeconds()));
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
  public Map<String, Aggregation> createAggregations(
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    return Map.of();
  }

  @Override
  public ViewResult retrieveResult(
      final SearchResponse<RawResult> response,
      final Map<String, Aggregate> aggs,
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    List<Hit<RawResult>> hits = response.hits().hits();
    if (context.isCsvExport()) {
      final int limit = context.getPagination().orElse(new PaginationDto()).getLimit();
      final List<Hit<RawResult>> rawResult = new ArrayList<>();
      osClient.scrollWith(response, rawResult::addAll, RawResult.class, limit);
      hits = rawResult.subList(0, min(limit, rawResult.size()));
    }
    final List<DecisionInstanceDto> rawDataDecisionInstanceDtos = transformHits(hits);
    final List<RawDataDecisionInstanceDto> rawData =
        rawDataSingleReportResultDtoMapper.mapFrom(
            rawDataDecisionInstanceDtos,
            getInputVariableEntries(context.getReportData()),
            getOutputVars(context.getReportData()));
    addNewVariablesAndDtoFieldsToTableColumnConfig(context, rawData);
    return ViewResult.builder().rawData(rawData).build();
  }

  @Override
  public ViewResult createEmptyResult(
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    return ViewResult.builder().rawData(new ArrayList<>()).build();
  }

  private List<DecisionInstanceDto> transformHits(final List<Hit<RawResult>> rawResult) {
    return rawResult.stream()
        .map(
            hit ->
                safe(
                    () ->
                        objectMapper.readValue(
                            objectMapper.writeValueAsString(hit.source()),
                            DecisionInstanceDto.class),
                    e ->
                        format(
                            "While mapping search results to class {} "
                                + "it was not possible to deserialize a hit from OpenSearch!"
                                + " Hit response from OpenSearch: "
                                + hit.source()),
                    LOG))
        .toList();
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
            .map(order -> transformSortOrder(order))
            .orElse(SortOrder.Desc);
    final SortOptions defaultSortOptions =
        new SortOptions.Builder()
            .field(new FieldSort.Builder().field(sortByField).order(sortOrder).build())
            .build();

    List<SortOptions> sortOptions = List.of(defaultSortOptions);
    if (sortByField.startsWith(INPUT_VARIABLE_PREFIX)) {
      sortOptions = sortOptionsByInputVariable(sortByField, sortOrder);
    } else if (sortByField.startsWith(OUTPUT_VARIABLE_PREFIX)) {
      sortOptions = sortOptionsByOutputVariable(sortByField, sortOrder);
    }

    searchRequestBuilder.sort(sortOptions);
  }

  private List<SortOptions> sortOptionsByInputVariable(
      final String sortByField, final SortOrder sortOrder) {
    final SortOptions defaultSortOptions =
        createSortByVariable(
            sortByField, sortOrder, INPUT_VARIABLE_PREFIX, INPUTS, VariableType.STRING);
    final List<SortOptions> variableSortOptions =
        getVariableMultivalueFields().stream()
            .map(
                type ->
                    createSortByVariable(
                        sortByField, sortOrder, INPUT_VARIABLE_PREFIX, INPUTS, type))
            .toList();
    final List<SortOptions> sortOptions = new ArrayList<>(variableSortOptions);
    // add default string field as last as it will always be present
    sortOptions.add(defaultSortOptions);
    return sortOptions;
  }

  private List<SortOptions> sortOptionsByOutputVariable(
      final String sortByField, final SortOrder sortOrder) {
    final SortOptions defaultSortOptions =
        createSortByVariable(
            sortByField, sortOrder, OUTPUT_VARIABLE_PREFIX, OUTPUTS, VariableType.STRING);
    final List<SortOptions> variableSortOptions =
        getVariableMultivalueFields().stream()
            .map(
                type ->
                    createSortByVariable(
                        sortByField, sortOrder, OUTPUT_VARIABLE_PREFIX, OUTPUTS, type))
            .toList();
    final List<SortOptions> sortOptions = new ArrayList<>(variableSortOptions);
    // add default string field as last as it will always be present
    sortOptions.add(defaultSortOptions);
    return sortOptions;
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
    return new SortOptions.Builder()
        .field(
            new FieldSort.Builder()
                .field(variableValuePath)
                .nested(
                    new NestedSortValue.Builder()
                        .path(variablePath)
                        .filter(term(variableIdPath, inputVariableId))
                        .build())
                .order(sortOrder)
                .build())
        .build();
  }

  @Override
  public DecisionVariableReader getDecisionVariableReader() {
    return decisionVariableReader;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.view.process;

import static io.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.VARIABLE_PREFIX;
import static io.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;
import static io.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.json;
import static io.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.scriptField;
import static io.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.sourceExclude;
import static io.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.term;
import static io.camunda.optimize.service.db.os.externalcode.client.dsl.UnitDSL.seconds;
import static io.camunda.optimize.service.db.os.writer.OpenSearchWriterUtil.createDefaultScript;
import static io.camunda.optimize.service.db.os.writer.OpenSearchWriterUtil.createDefaultScriptWithSpecificDtoParams;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLES;
import static io.camunda.optimize.service.db.util.ProcessVariableHelper.getNestedVariableNameField;
import static io.camunda.optimize.service.db.util.ProcessVariableHelper.getNestedVariableValueField;
import static io.camunda.optimize.service.exceptions.ExceptionHelper.safe;
import static java.lang.String.format;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.es.report.command.process.mapping.RawProcessDataResultDtoMapper;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.view.process.AbstractProcessViewRawDataInterpreter;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewResult;
import io.camunda.optimize.service.db.repository.os.VariableRepositoryOS;
import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.ScriptSortType;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.mapping.FieldType;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Conditional(OpenSearchCondition.class)
public class ProcessViewRawDataInterpreterOS extends AbstractProcessViewRawDataInterpreter
    implements ProcessViewInterpreterOS {
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;
  private final OptimizeOpenSearchClient osClient;
  private final DefinitionService definitionService;
  private final VariableRepositoryOS variableRepository;

  @Override
  public void adjustSearchRequest(
      final SearchRequest.Builder searchRequestBuilder,
      final Query baseQuery,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    context.setAllVariablesNames(allVariableNamesForMatchingInstances(baseQuery, context));
    searchRequestBuilder.source(b -> b.fetch(true));
    if (!context.isJsonExport()) {
      searchRequestBuilder.source(sourceExclude(FLOW_NODE_INSTANCES));
    }
    if (context.isCsvExport()) {
      context
          .getPagination()
          .ifPresent(
              pag ->
                  searchRequestBuilder.size(
                      pag.getLimit() > MAX_RESPONSE_SIZE_LIMIT
                          ? MAX_RESPONSE_SIZE_LIMIT
                          : pag.getLimit()));
      searchRequestBuilder.scroll(
          seconds(configurationService.getOpenSearchConfiguration().getScrollTimeoutInSeconds()));
    } else {
      context
          .getPagination()
          .ifPresent(
              pag -> {
                if (pag.getLimit() > MAX_RESPONSE_SIZE_LIMIT) {
                  pag.setLimit(MAX_RESPONSE_SIZE_LIMIT);
                }
                searchRequestBuilder.size(pag.getLimit()).from(pag.getOffset());
              });
    }

    Map<String, JsonData> params = new HashMap<>();
    params.put(CURRENT_TIME, json(LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli()));
    params.put(DATE_FORMAT, json(OPTIMIZE_DATE_FORMAT));
    searchRequestBuilder.scriptFields(
        Map.of(
            CURRENT_TIME,
            scriptField(createDefaultScriptWithSpecificDtoParams(PARAMS_CURRENT_TIME, params)),
            NUMBER_OF_USER_TASKS,
            scriptField(createDefaultScript(NUMBER_OF_USER_TASKS_SCRIPT)),
            FLOW_NODE_IDS_TO_DURATIONS,
            scriptField(
                createDefaultScriptWithSpecificDtoParams(GET_FLOW_NODE_DURATIONS_SCRIPT, params))));

    addSorting(sortByField(context), sortOrder(context), searchRequestBuilder, params);
  }

  @Override
  public Map<String, Aggregation> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return Map.of();
  }

  @Override
  public ViewResult retrieveResult(
      final SearchResponse<RawResult> response,
      final Map<String, Aggregate> aggs,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Map<String, Map<String, Long>> processInstanceIdsToFlowNodeIdsAndDurations =
        new HashMap<>();
    final Map<String, Long> instanceIdsToUserTaskCount = new HashMap<>();
    final List<ProcessInstanceDto> rawDataProcessInstanceDtos;
    if (context.isCsvExport()) {
      final int limit = context.getPagination().orElse(new PaginationDto()).getLimit();
      final List<Hit<RawResult>> hits = new ArrayList<>();
      osClient.scrollWith(response, hits::addAll, RawResult.class, limit);
      rawDataProcessInstanceDtos = transformHits(hits);
    } else {
      rawDataProcessInstanceDtos =
          response.hits().hits().stream()
              .map(
                  mappingFunction(
                      processInstanceIdsToFlowNodeIdsAndDurations,
                      instanceIdsToUserTaskCount,
                      context))
              .toList();
    }

    RawProcessDataResultDtoMapper rawDataSingleReportResultDtoMapper =
        new RawProcessDataResultDtoMapper();
    Map<String, String> flowNodeIdsToFlowNodeNames =
        definitionService.fetchDefinitionFlowNodeNamesAndIdsForProcessInstances(
            rawDataProcessInstanceDtos);
    final List<RawDataProcessInstanceDto> rawData =
        rawDataSingleReportResultDtoMapper.mapFrom(
            rawDataProcessInstanceDtos,
            objectMapper,
            context.getAllVariablesNames(),
            instanceIdsToUserTaskCount,
            processInstanceIdsToFlowNodeIdsAndDurations,
            flowNodeIdsToFlowNodeNames,
            !context.isJsonExport());

    addNewVariablesAndDtoFieldsToTableColumnConfig(context, rawData);
    return ViewResult.builder().rawData(rawData).build();
  }

  private <R> R getField(Hit<RawResult> hit, String field) {
    try {
      final List<R> values =
          objectMapper.readValue(hit.fields().get(field).toJson().toString(), List.class);
      return values.get(0);
    } catch (Exception e) {
      throw new OptimizeRuntimeException(format("Failed to extract %s from response!", field), e);
    }
  }

  private Function<Hit<RawResult>, ProcessInstanceDto> mappingFunction(
      final Map<String, Map<String, Long>> processInstanceIdsToFlowNodeIdsAndDurations,
      final Map<String, Long> instanceIdsToUserTaskCount,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return hit -> {
      try {
        final ProcessInstanceDto processInstance = transformHit(hit);
        final Map<String, Long> flowNodeIdsToDurations = getField(hit, FLOW_NODE_IDS_TO_DURATIONS);
        final Long numberOfUserTasks =
            this.<Integer>getField(hit, NUMBER_OF_USER_TASKS).longValue();

        processInstanceIdsToFlowNodeIdsAndDurations.put(
            processInstance.getProcessInstanceId(), flowNodeIdsToDurations);
        instanceIdsToUserTaskCount.put(processInstance.getProcessInstanceId(), numberOfUserTasks);
        if (processInstance.getDuration() == null && processInstance.getStartDate() != null) {
          final Optional<ReportSortingDto> sorting = context.getReportConfiguration().getSorting();
          if (sorting.isPresent()
              && sorting.get().getBy().isPresent()
              && ProcessInstanceIndex.DURATION.equals(sorting.get().getBy().get())) {
            processInstance.setDuration(Math.round(Double.parseDouble(hit.sort().get(0))));
          } else {
            long currentTime = this.<Long>getField(hit, CURRENT_TIME);
            processInstance.setDuration(
                currentTime - processInstance.getStartDate().toInstant().toEpochMilli());
          }
        }
        return processInstance;
      } catch (final NumberFormatException exception) {
        throw new OptimizeRuntimeException("Error while parsing fields to numbers");
      }
    };
  }

  private Set<String> allVariableNamesForMatchingInstances(
      final Query baseQuery,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final BoolQuery.Builder variableQuery = new BoolQuery.Builder().must(baseQuery);
    // we do not fetch the variable labels as part of the /evaluate
    // endpoint, but the frontend will query the /variables endpoint
    // to fetch them
    return variableRepository
        .getVariableNamesForInstancesMatchingQuery(
            defKeysToTarget(context.getReportData().getDefinitions()), variableQuery, Map.of())
        .stream()
        .map(ProcessVariableNameResponseDto::getName)
        .collect(Collectors.toSet());
  }

  private SortOrder sortOrder(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return context
        .getReportConfiguration()
        .getSorting()
        .flatMap(ReportSortingDto::getOrder)
        .map(QueryDSL::transformSortOrder)
        .map(order -> SortOrder.valueOf(order.name()))
        .orElse(SortOrder.Desc);
  }

  private List<ProcessInstanceDto> transformHits(final List<Hit<RawResult>> rawResult) {
    return rawResult.stream().map(this::transformHit).toList();
  }

  private ProcessInstanceDto transformHit(final Hit<RawResult> hit) {
    return safe(
        () ->
            objectMapper.readValue(
                objectMapper.writeValueAsString(hit.source()), ProcessInstanceDto.class),
        e ->
            format(
                "While mapping search results to class {} "
                    + "it was not possible to deserialize a hit from OpenSearch!"
                    + " Hit response from OpenSearch: "
                    + hit.source()),
        log);
  }

  private void addSorting(
      String sortByField,
      SortOrder sortOrder,
      SearchRequest.Builder searchRequestBuilder,
      Map<String, JsonData> params) {
    if (sortByField.startsWith(VARIABLE_PREFIX)) {
      final String variableName = sortByField.substring(VARIABLE_PREFIX.length());
      searchRequestBuilder.sort(
          SortOptions.of(
              so ->
                  so.field(
                      f ->
                          f.field(getNestedVariableValueField())
                              .order(sortOrder)
                              .nested(
                                  n ->
                                      n.path(VARIABLES)
                                          .filter(
                                              term(getNestedVariableNameField(), variableName))))));
    } else if (sortByField.equals(ProcessInstanceIndex.DURATION)) {
      params.put("duration", json(ProcessInstanceIndex.DURATION));
      params.put("startDate", json(ProcessInstanceIndex.START_DATE));
      Script script = createDefaultScriptWithSpecificDtoParams(SORT_SCRIPT, params);
      searchRequestBuilder.sort(
          SortOptions.of(
              so -> so.script(s -> s.script(script).type(ScriptSortType.Number).order(sortOrder))));
    } else {
      searchRequestBuilder.sort(
          SortOptions.of(
              so ->
                  so.field(
                      f ->
                          f.field(sortByField)
                              .order(sortOrder)
                              // this ensures the query doesn't fail on unknown properties but just
                              // ignores them
                              // this is done to ensure consistent behavior compared to unknown
                              // variable names as ES
                              // doesn't fail there
                              // @formatter:off
                              // https://www.elastic.co/guide/en/elasticsearch/reference/6.0/search-request-sort.html#_ignoring_unmapped_fields
                              .unmappedType(FieldType.Short))));
    }
  }
}

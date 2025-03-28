/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.view.process;

import static io.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.VARIABLE_PREFIX;
import static io.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;
import static io.camunda.optimize.service.db.es.report.interpreter.util.SortUtilsES.getSortOrder;
import static io.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil.createDefaultScript;
import static io.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil.createDefaultScriptWithSpecificDtoParams;
import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_RAW_DATA;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLES;
import static io.camunda.optimize.service.db.util.ProcessVariableHelper.getNestedVariableNameField;
import static io.camunda.optimize.service.db.util.ProcessVariableHelper.getNestedVariableValueField;

import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.ScriptField;
import co.elastic.clients.elasticsearch._types.ScriptSortType;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.mapping.FieldType;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import io.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.reader.ElasticsearchReaderUtil;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.util.RawProcessDataResultDtoMapper;
import io.camunda.optimize.service.db.report.interpreter.view.process.AbstractProcessViewRawDataInterpreter;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessView;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.db.repository.es.VariableRepositoryES;
import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessViewRawDataInterpreterES extends AbstractProcessViewRawDataInterpreter
    implements ProcessViewInterpreterES {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ProcessViewRawDataInterpreterES.class);
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;
  private final OptimizeElasticsearchClient esClient;
  private final DefinitionService definitionService;
  private final VariableRepositoryES variableRepository;

  public ProcessViewRawDataInterpreterES(
      final ConfigurationService configurationService,
      final ObjectMapper objectMapper,
      final OptimizeElasticsearchClient esClient,
      final DefinitionService definitionService,
      final VariableRepositoryES variableRepository) {
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
    this.esClient = esClient;
    this.definitionService = definitionService;
    this.variableRepository = variableRepository;
  }

  @Override
  public Set<ProcessView> getSupportedViews() {
    return Set.of(PROCESS_VIEW_RAW_DATA);
  }

  @Override
  public CompositeCommandResult.ViewResult createEmptyResult(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return CompositeCommandResult.ViewResult.builder().rawData(new ArrayList<>()).build();
  }

  @Override
  public void adjustSearchRequest(
      final SearchRequest.Builder searchRequestBuilder,
      final BoolQuery.Builder baseQueryBuilder,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {

    final List<String> defKeysToTarget =
        context.getReportData().getDefinitions().stream()
            .map(ReportDataDefinitionDto::getKey)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    final Supplier<BoolQuery.Builder> builderSupplier =
        () -> {
          final BoolQuery.Builder variableQuery = new BoolQuery.Builder();
          variableQuery.must(m -> m.bool(b -> baseQueryBuilder));
          // we do not fetch the variable labels as part of the /evaluate
          // endpoint, but the frontend will query the /variables endpoint
          // to fetch them
          return variableQuery;
        };
    final Set<String> allVariableNamesForMatchingInstances =
        variableRepository
            .getVariableNamesForInstancesMatchingQuery(
                defKeysToTarget, builderSupplier, Collections.emptyMap())
            .stream()
            .map(ProcessVariableNameResponseDto::getName)
            .collect(Collectors.toSet());
    context.setAllVariablesNames(allVariableNamesForMatchingInstances);

    final String sortByField =
        context
            .getReportConfiguration()
            .getSorting()
            .flatMap(ReportSortingDto::getBy)
            .orElse(ProcessInstanceIndex.START_DATE);
    final SortOrder sortOrder =
        context
            .getReportConfiguration()
            .getSorting()
            .flatMap(ReportSortingDto::getOrder)
            .map(order -> SortOrder.valueOf(order.name()))
            .orElse(SortOrder.DESC);

    searchRequestBuilder.source(
        s -> {
          s.fetch(true);
          if (!context.isJsonExport()) {
            s.filter(f -> f.excludes(FLOW_NODE_INSTANCES));
          }
          return s;
        });

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
          s ->
              s.time(
                  configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds()
                      + "s"));
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

    final Map<String, Object> params = new HashMap<>();
    params.put(CURRENT_TIME, LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli());
    params.put(DATE_FORMAT, OPTIMIZE_DATE_FORMAT);
    searchRequestBuilder.scriptFields(
        CURRENT_TIME,
        ScriptField.of(
            f -> f.script(createDefaultScriptWithSpecificDtoParams(PARAMS_CURRENT_TIME, params))));
    searchRequestBuilder.scriptFields(
        NUMBER_OF_USER_TASKS,
        ScriptField.of(f -> f.script(createDefaultScript(NUMBER_OF_USER_TASKS_SCRIPT))));
    searchRequestBuilder.scriptFields(
        FLOW_NODE_IDS_TO_DURATIONS,
        ScriptField.of(
            f ->
                f.script(
                    createDefaultScriptWithSpecificDtoParams(
                        GET_FLOW_NODE_DURATIONS_SCRIPT, params))));
    addSorting(sortByField, sortOrder, searchRequestBuilder, params);
  }

  @Override
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return Map.of();
  }

  @Override
  public CompositeCommandResult.ViewResult retrieveResult(
      final ResponseBody<?> response,
      final Map<String, Aggregate> aggs,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Map<String, Map<String, Long>> processInstanceIdsToFlowNodeIdsAndDurations =
        new HashMap<>();
    final Map<String, Long> instanceIdsToUserTaskCount = new HashMap<>();
    final Function<Hit<?>, ProcessInstanceDto> mappingFunction =
        hit -> {
          try {
            // TODO Condition here we need for support low request deserialization.
            // After new minor release we will remove it.
            final ProcessInstanceDto processInstance;
            if (hit.source() instanceof Map) {
              processInstance = objectMapper.convertValue(hit.source(), ProcessInstanceDto.class);
            } else if (hit.source() instanceof ProcessInstanceDto) {
              processInstance = (ProcessInstanceDto) hit.source();
            } else {
              processInstance =
                  objectMapper.readValue(hit.source().toString(), ProcessInstanceDto.class);
            }
            processInstanceIdsToFlowNodeIdsAndDurations.put(
                processInstance.getProcessInstanceId(),
                ((Map<String, Object>)
                        hit.fields().get(FLOW_NODE_IDS_TO_DURATIONS).to(List.class).get(0))
                    .entrySet().stream()
                        .collect(
                            Collectors.toMap(
                                Map.Entry::getKey, e -> Long.valueOf(e.getValue().toString()))));
            instanceIdsToUserTaskCount.put(
                processInstance.getProcessInstanceId(),
                Long.valueOf(
                    hit.fields().get(NUMBER_OF_USER_TASKS).to(List.class).get(0).toString()));
            if (processInstance.getDuration() == null && processInstance.getStartDate() != null) {
              final Optional<ReportSortingDto> sorting =
                  context.getReportConfiguration().getSorting();
              if (sorting.isPresent()
                  && sorting.get().getBy().isPresent()
                  && ProcessInstanceIndex.DURATION.equals(sorting.get().getBy().get())) {
                processInstance.setDuration(Math.round(hit.sort().get(0).doubleValue()));
              } else {
                final long currentTime =
                    Long.parseLong(hit.fields().get(CURRENT_TIME).to(List.class).get(0).toString());
                processInstance.setDuration(
                    currentTime - processInstance.getStartDate().toInstant().toEpochMilli());
              }
            }
            return processInstance;
          } catch (final NumberFormatException exception) {
            throw new OptimizeRuntimeException("Error while parsing fields to numbers");
          } catch (final JsonProcessingException e) {
            throw new RuntimeException(e);
          }
        };

    final List<ProcessInstanceDto> rawDataProcessInstanceDtos;
    if (context.isCsvExport()) {
      rawDataProcessInstanceDtos =
          ElasticsearchReaderUtil.retrieveScrollResultsTillLimit(
              response,
              ProcessInstanceDto.class,
              mappingFunction,
              esClient,
              configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds(),
              context.getPagination().orElse(new PaginationDto()).getLimit());
    } else {
      rawDataProcessInstanceDtos =
          ElasticsearchReaderUtil.mapHits(
              response.hits(), Integer.MAX_VALUE, ProcessInstanceDto.class, mappingFunction);
    }

    final RawProcessDataResultDtoMapper rawDataSingleReportResultDtoMapper =
        new RawProcessDataResultDtoMapper();
    final Map<String, String> flowNodeIdsToFlowNodeNames =
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
    return CompositeCommandResult.ViewResult.builder().rawData(rawData).build();
  }

  private void addSorting(
      final String sortByField,
      final SortOrder sortOrder,
      final SearchRequest.Builder searchRequestBuilder,
      final Map<String, Object> params) {
    if (sortByField.startsWith(VARIABLE_PREFIX)) {
      final String variableName = sortByField.substring(VARIABLE_PREFIX.length());
      searchRequestBuilder.sort(
          SortOptions.of(
              s ->
                  s.field(
                      f ->
                          f.field(getNestedVariableValueField())
                              .nested(
                                  n ->
                                      n.path(VARIABLES)
                                          .filter(
                                              ff ->
                                                  ff.term(
                                                      t ->
                                                          t.field(getNestedVariableNameField())
                                                              .value(variableName))))
                              .order(getSortOrder(sortOrder)))));
    } else if (sortByField.equals(ProcessInstanceIndex.DURATION)) {
      params.put("duration", ProcessInstanceIndex.DURATION);
      params.put("startDate", ProcessInstanceIndex.START_DATE);

      // when running the query, ES throws an error message for checking the existence of the value
      // of a field with
      // doc['field'].value == null
      // and recommends using doc['field'].size() == 0
      final Script script = createDefaultScriptWithSpecificDtoParams(SORT_SCRIPT, params);
      searchRequestBuilder.sort(
          s ->
              s.script(
                  ss ->
                      ss.script(script)
                          .type(ScriptSortType.Number)
                          .order(getSortOrder(sortOrder))));
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
}

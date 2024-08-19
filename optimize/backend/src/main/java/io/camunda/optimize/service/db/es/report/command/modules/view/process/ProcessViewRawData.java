/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.modules.view.process;

import static io.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.VARIABLE_PREFIX;
import static io.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;
import static io.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil.createDefaultScript;
import static io.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil.createDefaultScriptWithSpecificDtoParams;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLES;
import static io.camunda.optimize.service.export.CSVUtils.extractAllProcessInstanceDtoFieldKeys;
import static io.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableNameField;
import static io.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableValueField;
import static org.elasticsearch.core.TimeValue.timeValueSeconds;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import io.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.es.reader.ElasticsearchReaderUtil;
import io.camunda.optimize.service.db.es.report.command.exec.ExecutionContext;
import io.camunda.optimize.service.db.es.report.command.modules.result.CompositeCommandResult.ViewResult;
import io.camunda.optimize.service.db.es.report.command.process.mapping.RawProcessDataResultDtoMapper;
import io.camunda.optimize.service.db.reader.ProcessVariableReader;
import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.export.CSVUtils;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.NestedSortBuilder;
import org.elasticsearch.search.sort.ScriptSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessViewRawData extends ProcessViewPart {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(ProcessViewRawData.class);
  private static final String CURRENT_TIME = "currentTime";
  private static final String PARAMS_CURRENT_TIME = "params." + CURRENT_TIME;
  private static final String DATE_FORMAT = "dateFormat";
  private static final String FLOWNODE_IDS_TO_DURATIONS = "flowNodeIdsToDurations";
  private static final String NUMBER_OF_USER_TASKS = "numberOfUserTasks";
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;
  private final DatabaseClient databaseClient;
  private final DefinitionService definitionService;
  private final ProcessVariableReader processVariableReader;

  public ProcessViewRawData(
      final ConfigurationService configurationService,
      final ObjectMapper objectMapper,
      final DatabaseClient databaseClient,
      final DefinitionService definitionService,
      final ProcessVariableReader processVariableReader) {
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
    this.databaseClient = databaseClient;
    this.definitionService = definitionService;
    this.processVariableReader = processVariableReader;
  }

  @Override
  public void adjustSearchRequest(
      final SearchRequest searchRequest,
      final BoolQueryBuilder baseQuery,
      final ExecutionContext<ProcessReportDataDto> context) {
    super.adjustSearchRequest(searchRequest, baseQuery, context);

    final List<String> defKeysToTarget =
        context.getReportData().getDefinitions().stream()
            .map(ReportDataDefinitionDto::getKey)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    final BoolQueryBuilder variableQuery = boolQuery().must(baseQuery);
    // we do not fetch the variable labels as part of the /evaluate
    // endpoint, but the frontend will query the /variables endpoint
    // to fetch them
    final Set<String> allVariableNamesForMatchingInstances =
        processVariableReader
            .getVariableNamesForInstancesMatchingQuery(
                defKeysToTarget, variableQuery, Collections.emptyMap())
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

    final SearchSourceBuilder search = searchRequest.source().fetchSource(true);
    if (!context.isJsonExport()) {
      search.fetchSource(null, new String[] {FLOW_NODE_INSTANCES});
    }
    if (context.isCsvExport()) {
      context
          .getPagination()
          .ifPresent(
              pag ->
                  search.size(
                      pag.getLimit() > MAX_RESPONSE_SIZE_LIMIT
                          ? MAX_RESPONSE_SIZE_LIMIT
                          : pag.getLimit()));
      searchRequest.scroll(
          timeValueSeconds(
              configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds()));
    } else {
      context
          .getPagination()
          .ifPresent(
              pag -> {
                if (pag.getLimit() > MAX_RESPONSE_SIZE_LIMIT) {
                  pag.setLimit(MAX_RESPONSE_SIZE_LIMIT);
                }
                search.size(pag.getLimit()).from(pag.getOffset());
              });
    }
    // @formatter:off
    final String getFlowNodeDurationsScript =
        "def flowNodeInstanceIdToDuration = new HashMap();"
            + "def dateFormatter = new SimpleDateFormat(params.dateFormat);"
            + "for (flowNodeInstance in params._source.flowNodeInstances) {"
            + "  if (flowNodeInstance.totalDurationInMs != null) {"
            + "    if (flowNodeInstanceIdToDuration.containsKey(flowNodeInstance.flowNodeId)) {"
            + "      def currentDuration = flowNodeInstanceIdToDuration.get(flowNodeInstance.flowNodeId);"
            + "      flowNodeInstanceIdToDuration.put(flowNodeInstance.flowNodeId, flowNodeInstance.totalDurationInMs + currentDuration)"
            + "    } else {"
            + "      flowNodeInstanceIdToDuration.put(flowNodeInstance.flowNodeId, flowNodeInstance.totalDurationInMs)"
            + "    }"
            + "  } else {"
            + "    if (flowNodeInstance.startDate != null) {"
            + "      def duration = params.currentTime - dateFormatter.parse(flowNodeInstance.startDate).getTime();"
            + "      if (flowNodeInstanceIdToDuration.containsKey(flowNodeInstance.flowNodeId)) {"
            + "        def currentDuration = flowNodeInstanceIdToDuration.get(flowNodeInstance.flowNodeId);"
            + "        flowNodeInstanceIdToDuration.put(flowNodeInstance.flowNodeId, duration + currentDuration)"
            + "      } else {"
            + "        flowNodeInstanceIdToDuration.put(flowNodeInstance.flowNodeId, duration)"
            + "      }"
            + "    }"
            + "  }"
            + "}"
            + "return flowNodeInstanceIdToDuration;";
    // @formatter:on

    final Map<String, Object> params = new HashMap<>();
    params.put(CURRENT_TIME, LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli());
    params.put(DATE_FORMAT, OPTIMIZE_DATE_FORMAT);
    searchRequest
        .source()
        .scriptField(
            CURRENT_TIME,
            createDefaultScriptWithSpecificDtoParams(PARAMS_CURRENT_TIME, params, objectMapper));
    searchRequest
        .source()
        .scriptField(
            NUMBER_OF_USER_TASKS,
            createDefaultScript(
                "Optional.ofNullable(params._source.flowNodeInstances).map(list -> list.stream().filter(item -> item.flowNodeType"
                    + ".equals('userTask')).count()).orElse(0L)"));
    searchRequest
        .source()
        .scriptField(
            FLOWNODE_IDS_TO_DURATIONS,
            createDefaultScriptWithSpecificDtoParams(
                getFlowNodeDurationsScript, params, objectMapper));
    addSorting(sortByField, sortOrder, searchRequest.source(), params);
  }

  @Override
  public ViewProperty getViewProperty(final ExecutionContext<ProcessReportDataDto> context) {
    return ViewProperty.RAW_DATA;
  }

  @Override
  public List<AggregationBuilder> createAggregations(
      final ExecutionContext<ProcessReportDataDto> context) {
    return Collections.emptyList();
  }

  @Override
  public ViewResult retrieveResult(
      final SearchResponse response,
      final Aggregations aggs,
      final ExecutionContext<ProcessReportDataDto> context) {
    final Map<String, Map<String, Long>> processInstanceIdsToFlowNodeIdsAndDurations =
        new HashMap<>();
    final Map<String, Long> instanceIdsToUserTaskCount = new HashMap<>();
    final Function<SearchHit, ProcessInstanceDto> mappingFunction =
        hit -> {
          try {
            final ProcessInstanceDto processInstance =
                objectMapper.readValue(hit.getSourceAsString(), ProcessInstanceDto.class);
            processInstanceIdsToFlowNodeIdsAndDurations.put(
                processInstance.getProcessInstanceId(),
                hit.getFields().get(FLOWNODE_IDS_TO_DURATIONS).getValue());
            instanceIdsToUserTaskCount.put(
                processInstance.getProcessInstanceId(),
                Long.valueOf(hit.getFields().get(NUMBER_OF_USER_TASKS).getValue().toString()));
            if (processInstance.getDuration() == null && processInstance.getStartDate() != null) {
              final Optional<ReportSortingDto> sorting =
                  context.getReportConfiguration().getSorting();
              if (sorting.isPresent()
                  && sorting.get().getBy().isPresent()
                  && ProcessInstanceIndex.DURATION.equals(sorting.get().getBy().get())) {
                processInstance.setDuration(
                    Math.round(Double.parseDouble(hit.getSortValues()[0].toString())));
              } else {
                final Long currentTime = hit.getFields().get(CURRENT_TIME).getValue();
                processInstance.setDuration(
                    currentTime - processInstance.getStartDate().toInstant().toEpochMilli());
              }
            }
            return processInstance;
          } catch (final NumberFormatException exception) {
            throw new OptimizeRuntimeException("Error while parsing fields to numbers");
          } catch (final IOException e) {
            final String reason = "Error while mapping search results to Process Instances";
            log.error(reason, e);
            throw new OptimizeRuntimeException(reason);
          }
        };

    final List<ProcessInstanceDto> rawDataProcessInstanceDtos;
    if (context.isCsvExport()) {
      rawDataProcessInstanceDtos =
          ElasticsearchReaderUtil.retrieveScrollResultsTillLimit(
              response,
              ProcessInstanceDto.class,
              mappingFunction,
              databaseClient,
              configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds(),
              context.getPagination().orElse(new PaginationDto()).getLimit());
    } else {
      rawDataProcessInstanceDtos =
          ElasticsearchReaderUtil.mapHits(
              response.getHits(), Integer.MAX_VALUE, ProcessInstanceDto.class, mappingFunction);
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
            flowNodeIdsToFlowNodeNames);

    addNewVariablesAndDtoFieldsToTableColumnConfig(context, rawData);
    return ViewResult.builder().rawData(rawData).build();
  }

  @Override
  public void addViewAdjustmentsForCommandKeyGeneration(
      final ProcessReportDataDto dataForCommandKey) {
    dataForCommandKey.setView(new ProcessViewDto(ViewProperty.RAW_DATA));
  }

  @Override
  public ViewResult createEmptyResult(final ExecutionContext<ProcessReportDataDto> context) {
    return ViewResult.builder().rawData(new ArrayList<>()).build();
  }

  private void addSorting(
      final String sortByField,
      final SortOrder sortOrder,
      final SearchSourceBuilder searchSourceBuilder,
      final Map<String, Object> params) {
    if (sortByField.startsWith(VARIABLE_PREFIX)) {
      final String variableName = sortByField.substring(VARIABLE_PREFIX.length());
      searchSourceBuilder.sort(
          SortBuilders.fieldSort(getNestedVariableValueField())
              .setNestedSort(
                  new NestedSortBuilder(VARIABLES)
                      .setFilter(termQuery(getNestedVariableNameField(), variableName)))
              .order(sortOrder));
    } else if (sortByField.equals(ProcessInstanceIndex.DURATION)) {
      params.put("duration", ProcessInstanceIndex.DURATION);
      params.put("startDate", ProcessInstanceIndex.START_DATE);
      // when running the query, ES throws an error message for checking the existence of the value
      // of a field with
      // doc['field'].value == null
      // and recommends using doc['field'].size() == 0
      // @formatter:off
      final String query =
          "if (doc[params.duration].size() == 0) {"
              + "params.currentTime - doc[params.startDate].value.toInstant()"
              + " .toEpochMilli() }"
              + "else { "
              + " doc[params.duration].value"
              + "}";
      // @formatter:on

      final Script script = createDefaultScriptWithSpecificDtoParams(query, params, objectMapper);
      searchSourceBuilder.sort(
          SortBuilders.scriptSort(script, ScriptSortBuilder.ScriptSortType.NUMBER)
              .order(sortOrder));
    } else {
      searchSourceBuilder.sort(
          SortBuilders.fieldSort(sortByField)
              .order(sortOrder)
              // this ensures the query doesn't fail on unknown properties but just ignores them
              // this is done to ensure consistent behavior compared to unknown variable names as ES
              // doesn't fail there
              // @formatter:off
              // https://www.elastic.co/guide/en/elasticsearch/reference/6.0/search-request-sort.html#_ignoring_unmapped_fields
              // @formatter:on
              .unmappedType("short"));
    }
  }

  private void addNewVariablesAndDtoFieldsToTableColumnConfig(
      final ExecutionContext<ProcessReportDataDto> context,
      final List<RawDataProcessInstanceDto> rawData) {
    final List<String> variableNames =
        rawData.stream()
            .flatMap(
                rawDataProcessInstanceDto ->
                    rawDataProcessInstanceDto.getVariables().keySet().stream())
            .map(varKey -> VARIABLE_PREFIX + varKey)
            .toList();

    final TableColumnDto tableColumns = context.getReportConfiguration().getTableColumns();
    tableColumns.addCountColumns(CSVUtils.extractAllPrefixedCountKeys());
    tableColumns.addNewAndRemoveUnexpectedVariableColumns(variableNames);
    tableColumns.addNewAndRemoveUnexpectedFlowNodeDurationColumns(
        CSVUtils.extractAllPrefixedFlowNodeKeys(rawData));
    tableColumns.addDtoColumns(extractAllProcessInstanceDtoFieldKeys());
  }
}

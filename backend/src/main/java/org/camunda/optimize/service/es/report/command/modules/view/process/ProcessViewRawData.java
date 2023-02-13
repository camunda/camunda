/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.modules.view.process;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.reader.ElasticsearchReaderUtil;
import org.camunda.optimize.service.es.reader.ProcessVariableReader;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.ViewResult;
import org.camunda.optimize.service.es.report.command.process.mapping.RawProcessDataResultDtoMapper;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
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
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.VARIABLE_PREFIX;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil.createDefaultScript;
import static org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil.createDefaultScriptWithSpecificDtoParams;
import static org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil.createDefaultScript;
import static org.camunda.optimize.service.export.CSVUtils.extractAllProcessInstanceDtoFieldKeys;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableNameField;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableValueField;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAX_RESPONSE_SIZE_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.elasticsearch.core.TimeValue.timeValueSeconds;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Slf4j
@RequiredArgsConstructor
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessViewRawData extends ProcessViewPart {

  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;
  private final OptimizeElasticsearchClient esClient;
  private final DefinitionService definitionService;
  private final ProcessVariableReader processVariableReader;
  private static final String CURRENT_TIME = "currentTime";
  private static final String PARAMS_CURRENT_TIME = "params." + CURRENT_TIME;
  private static final String DATE_FORMAT = "dateFormat";
  private static final String FLOWNODE_IDS_TO_DURATIONS = "flowNodeIdsToDurations";
  private static final String NUMBER_OF_USERTASKS = "numberOfUserTasks";

  @Override
  public ViewProperty getViewProperty(final ExecutionContext<ProcessReportDataDto> context) {
    return ViewProperty.RAW_DATA;
  }

  @Override
  public void adjustSearchRequest(final SearchRequest searchRequest,
                                  final BoolQueryBuilder baseQuery,
                                  final ExecutionContext<ProcessReportDataDto> context) {
    super.adjustSearchRequest(searchRequest, baseQuery, context);

    final BoolQueryBuilder variableQuery = boolQuery().must(baseQuery);
    // we do not fetch the variable labels as part of the /evaluate
    // endpoint, but the frontend will query the /variables endpoint
    // to fetch them
    final Set<String> allVariableNamesForMatchingInstances =
      processVariableReader.getVariableNamesForInstancesMatchingQuery(variableQuery, Collections.emptyMap())
        .stream()
        .map(ProcessVariableNameResponseDto::getName)
        .collect(Collectors.toSet());
    context.setAllVariablesNames(allVariableNamesForMatchingInstances);

    final String sortByField = context.getReportConfiguration().getSorting()
      .flatMap(ReportSortingDto::getBy)
      .orElse(ProcessInstanceIndex.START_DATE);
    final SortOrder sortOrder = context.getReportConfiguration().getSorting()
      .flatMap(ReportSortingDto::getOrder)
      .map(order -> SortOrder.valueOf(order.name()))
      .orElse(SortOrder.DESC);

    final SearchSourceBuilder search = searchRequest.source()
      .fetchSource(true)
      .fetchSource(null, new String[]{FLOW_NODE_INSTANCES});
    if (context.isCsvExport()) {
      context.getPagination()
        .ifPresent(pag -> search.size(pag.getLimit() > MAX_RESPONSE_SIZE_LIMIT ?
                                        MAX_RESPONSE_SIZE_LIMIT : pag.getLimit()));
      searchRequest.scroll(timeValueSeconds(configurationService.getEsScrollTimeoutInSeconds()));
    } else {
      context.getPagination().ifPresent(pag -> {
        if (pag.getLimit() > MAX_RESPONSE_SIZE_LIMIT) {
          pag.setLimit(MAX_RESPONSE_SIZE_LIMIT);
        }
        search
          .size(pag.getLimit())
          .from(pag.getOffset());
      });
    }
    String getFlowNodeDurationsScript =
        "def flowNodeInstanceIdToDuration = new HashMap();" +
        "def dateFormatter = new SimpleDateFormat(params.dateFormat);" +
          "for (flowNodeInstance in params._source.flowNodeInstances) {" +
            "if (flowNodeInstance.totalDurationInMs != null) {" +
              "if (flowNodeInstanceIdToDuration.containsKey(flowNodeInstance.flowNodeId)) {" +
                "def currentDuration = flowNodeInstanceIdToDuration.get(flowNodeInstance.flowNodeId);" +
                "flowNodeInstanceIdToDuration.put(flowNodeInstance.flowNodeId, flowNodeInstance.totalDurationInMs + currentDuration)" +
              "} else {" +
                "flowNodeInstanceIdToDuration.put(flowNodeInstance.flowNodeId, flowNodeInstance.totalDurationInMs)" +
                "}" +
            "} else {" +
              "if (flowNodeInstance.startDate != null) {" +
                "def duration = params.currentTime - dateFormatter.parse(flowNodeInstance.startDate).getTime();" +
                "if (flowNodeInstanceIdToDuration.containsKey(flowNodeInstance.flowNodeId)) {" +
                  "def currentDuration = flowNodeInstanceIdToDuration.get(flowNodeInstance.flowNodeId);" +
                  "flowNodeInstanceIdToDuration.put(flowNodeInstance.flowNodeId, duration + currentDuration)" +
                "} else {" +
                  "flowNodeInstanceIdToDuration.put(flowNodeInstance.flowNodeId, duration)" +
                "}" +
              "}" +
            "}" +
          "}" +
        "return flowNodeInstanceIdToDuration;";

    Map<String, Object> params = new HashMap<>();
    params.put(CURRENT_TIME, LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli());
    params.put(DATE_FORMAT, OPTIMIZE_DATE_FORMAT);
    searchRequest.source().scriptField(
      CURRENT_TIME,
      createDefaultScriptWithSpecificDtoParams(PARAMS_CURRENT_TIME, params, objectMapper)
    );
    searchRequest.source().scriptField(
      NUMBER_OF_USERTASKS,
      createDefaultScript("Optional.ofNullable(params._source.flowNodeInstances).map(list -> list.stream().filter(item -> item.flowNodeType.equals('userTask')).count()).orElse(0L)")
    );
    searchRequest.source().scriptField(
      FLOWNODE_IDS_TO_DURATIONS,
      createDefaultScriptWithSpecificDtoParams(getFlowNodeDurationsScript, params, objectMapper)
    );
    addSorting(sortByField, sortOrder, searchRequest.source(), params);
  }

  @Override
  public List<AggregationBuilder> createAggregations(final ExecutionContext<ProcessReportDataDto> context) {
    return Collections.emptyList();
  }

  @Override
  public ViewResult retrieveResult(final SearchResponse response,
                                   final Aggregations aggs,
                                   final ExecutionContext<ProcessReportDataDto> context) {
    Map<String, Map<String, Long>> processInstanceIdsToFlowNodeIdsAndDurations = new HashMap<>();
    Map<String, Long> instanceIdsToUserTaskCount = new HashMap<>();
    Function<SearchHit, ProcessInstanceDto> mappingFunction = hit -> {
      try {
        final ProcessInstanceDto processInstance = objectMapper.readValue(
          hit.getSourceAsString(),
          ProcessInstanceDto.class
        );
        processInstanceIdsToFlowNodeIdsAndDurations.put(
          processInstance.getProcessInstanceId(),
          hit.getFields().get(FLOWNODE_IDS_TO_DURATIONS).getValue()
        );
        instanceIdsToUserTaskCount.put(processInstance.getProcessInstanceId(), Long.valueOf(hit.getFields().get(NUMBER_OF_USERTASKS).getValue().toString()));
        if (processInstance.getDuration() == null && processInstance.getStartDate() != null) {
          final Optional<ReportSortingDto> sorting = context.getReportConfiguration().getSorting();
          if (sorting.isPresent() && sorting.get().getBy().isPresent()
            && ProcessInstanceIndex.DURATION.equals(sorting.get().getBy().get())) {
            processInstance.setDuration(Math.round(Double.parseDouble(hit.getSortValues()[0].toString())));
          } else {
            Long currentTime = hit.getFields().get(CURRENT_TIME).getValue();
            processInstance.setDuration(currentTime - processInstance.getStartDate().toInstant().toEpochMilli());
          }
        }
        return processInstance;
      } catch (IOException e) {
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
          esClient,
          configurationService.getEsScrollTimeoutInSeconds(),
          context.getPagination().orElse(new PaginationDto()).getLimit()
        );
    } else {
      rawDataProcessInstanceDtos = ElasticsearchReaderUtil.mapHits(
        response.getHits(),
        Integer.MAX_VALUE,
        ProcessInstanceDto.class,
        mappingFunction
      );
    }

    RawProcessDataResultDtoMapper rawDataSingleReportResultDtoMapper = new RawProcessDataResultDtoMapper();
    Map<String, String> flowNodeIdsToFlowNodeNames = definitionService.fetchDefinitionFlowNodeNamesAndIdsForProcessInstances(
      rawDataProcessInstanceDtos);
    final List<RawDataProcessInstanceDto> rawData = rawDataSingleReportResultDtoMapper.mapFrom(
      rawDataProcessInstanceDtos,
      objectMapper,
      context.getAllVariablesNames(),
      instanceIdsToUserTaskCount,
      processInstanceIdsToFlowNodeIdsAndDurations,
      flowNodeIdsToFlowNodeNames
    );

    addNewVariablesAndDtoFieldsToTableColumnConfig(context, rawData);
    return ViewResult.builder().rawData(rawData).build();
  }

  @Override
  public ViewResult createEmptyResult(final ExecutionContext<ProcessReportDataDto> context) {
    return ViewResult.builder().rawData(new ArrayList<>()).build();
  }

  @Override
  public void addViewAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto dataForCommandKey) {
    dataForCommandKey.setView(new ProcessViewDto(ViewProperty.RAW_DATA));
  }

  private void addSorting(String sortByField, SortOrder sortOrder, SearchSourceBuilder searchSourceBuilder,
                          Map<String, Object> params) {
    if (sortByField.startsWith(VARIABLE_PREFIX)) {
      final String variableName = sortByField.substring(VARIABLE_PREFIX.length());
      searchSourceBuilder.sort(
        SortBuilders
          .fieldSort(getNestedVariableValueField())
          .setNestedSort(
            new NestedSortBuilder(VARIABLES)
              .setFilter(termQuery(getNestedVariableNameField(), variableName))
          ).order(sortOrder));
    } else if (sortByField.equals(ProcessInstanceIndex.DURATION)) {
      params.put("duration", ProcessInstanceIndex.DURATION);
      params.put("startDate", ProcessInstanceIndex.START_DATE);
      // when running the query, ES throws an error message for checking the existence of the value of a field with
      // doc['field'].value == null
      // and recommends using doc['field'].size() == 0
      //@formatter:off
      String query =
        "if (doc[params.duration].size() == 0) {" +
        "params.currentTime - doc[params.startDate].value.toInstant()" +
        " .toEpochMilli() }" +
        "else { " +
        " doc[params.duration].value" +
        "}";
      //@formatter:on

      Script script = createDefaultScriptWithSpecificDtoParams(query, params, objectMapper);
      searchSourceBuilder.sort(
        SortBuilders.scriptSort(
          script,
          ScriptSortBuilder.ScriptSortType.NUMBER
        ).order(sortOrder));
    } else {
      searchSourceBuilder.sort(
        SortBuilders.fieldSort(sortByField).order(sortOrder)
          // this ensures the query doesn't fail on unknown properties but just ignores them
          // this is done to ensure consistent behavior compared to unknown variable names as ES doesn't fail there
          // @formatter:off
          // https://www.elastic.co/guide/en/elasticsearch/reference/6.0/search-request-sort.html#_ignoring_unmapped_fields
          // @formatter:on
          .unmappedType("short")
      );
    }
  }

  private void addNewVariablesAndDtoFieldsToTableColumnConfig(final ExecutionContext<ProcessReportDataDto> context,
                                                              final List<RawDataProcessInstanceDto> rawData) {
    final List<String> variableNames = rawData
      .stream()
      .flatMap(rawDataProcessInstanceDto -> rawDataProcessInstanceDto.getVariables().keySet().stream())
      .map(varKey -> VARIABLE_PREFIX + varKey)
      .collect(toList());

    TableColumnDto tableColumns = context.getReportConfiguration().getTableColumns();
    tableColumns.addNewAndRemoveUnexpectedVariableColumns(variableNames);
    tableColumns.addDtoColumns(extractAllProcessInstanceDtoFieldKeys());
  }
}

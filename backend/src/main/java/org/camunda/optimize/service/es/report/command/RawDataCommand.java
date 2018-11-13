package org.camunda.optimize.service.es.report.command;

import org.camunda.optimize.dto.optimize.query.report.single.parameters.SortingDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.raw.RawDataSingleReportResultDto;
import org.camunda.optimize.service.es.report.command.mapping.RawDataSingleReportResultDtoMapper;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.service.util.VariableHelper;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;

import java.util.Optional;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.EVENTS;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.VARIABLE_NAME;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.VARIABLE_VALUE;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

public class RawDataCommand extends ReportCommand<RawDataSingleReportResultDto> {
  private static final String VARIABLE_PREFIX = "variable:";

  private static final Long RAW_DATA_LIMIT = 1_000L;

  private final RawDataSingleReportResultDtoMapper rawDataSingleReportResultDtoMapper =
    new RawDataSingleReportResultDtoMapper(RAW_DATA_LIMIT);

  public RawDataSingleReportResultDto evaluate() {
    logger.debug(
      "Evaluating raw data report for process definition key [{}] and version [{}]",
      reportData.getProcessDefinitionKey(),
      reportData.getProcessDefinitionVersion()
    );

    BoolQueryBuilder query = setupBaseQuery(
      reportData.getProcessDefinitionKey(),
      reportData.getProcessDefinitionVersion()
    );
    queryFilterEnhancer.addFilterToQuery(query, reportData.getFilter());


    final Optional<SortingDto> customSorting = Optional.ofNullable(reportData.getParameters())
      .flatMap(parameters -> Optional.ofNullable(parameters.getSorting()));
    final String sortByField = customSorting.flatMap(sorting -> Optional.ofNullable(sorting.getBy()))
      .orElse(ProcessInstanceType.START_DATE);
    final SortOrder sortOrder = customSorting.flatMap(sorting -> Optional.ofNullable(sorting.getOrder()))
      .map(order -> SortOrder.valueOf(order.name()))
      .orElse(SortOrder.DESC);

    final SearchRequestBuilder searchRequestBuilder = esclient
      .prepareSearch(getOptimizeIndexAliasForType(configurationService.getProcessInstanceType()))
      .setTypes(configurationService.getProcessInstanceType())
      .setQuery(query)
      .setFetchSource(null, EVENTS)
      .setSize(RAW_DATA_LIMIT.intValue());

    if (sortByField.startsWith(VARIABLE_PREFIX)) {
      final String variableName = sortByField.substring(VARIABLE_PREFIX.length());
      for (String variableField : VariableHelper.getAllVariableTypeFieldLabels()) {
        searchRequestBuilder.addSort(
          SortBuilders
            .fieldSort(variableField + "." + VARIABLE_VALUE)
            .setNestedPath(variableField)
            .setNestedFilter(
              termQuery(variableField + "." + VARIABLE_NAME, variableName)
            )
            .order(sortOrder)
        );
      }
    } else {
      searchRequestBuilder.addSort(
        SortBuilders.fieldSort(sortByField).order(sortOrder)
          // this ensures the query doesn't fail on unknown properties but just ignores them
          // this is done to ensure consistent behavior compared to unknown variable names as ES doesn't fail there
          // https://www.elastic.co/guide/en/elasticsearch/reference/6.0/search-request-sort.html#_ignoring_unmapped_fields
          .unmappedType("short")
      );
    }

    final SearchResponse scrollResp = searchRequestBuilder.get();

    return rawDataSingleReportResultDtoMapper.mapFrom(scrollResp, objectMapper);
  }

}

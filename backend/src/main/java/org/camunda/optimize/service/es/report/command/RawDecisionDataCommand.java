package org.camunda.optimize.service.es.report.command;

import org.camunda.optimize.dto.optimize.query.report.VariableType;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto;
import org.camunda.optimize.service.es.report.command.mapping.RawDecisionDataResultDtoMapper;
import org.camunda.optimize.service.es.schema.type.DecisionInstanceType;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;

import java.util.Optional;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.DecisionInstanceType.INPUTS;
import static org.camunda.optimize.service.es.schema.type.DecisionInstanceType.OUTPUTS;
import static org.camunda.optimize.service.util.DecisionVariableHelper.getVariableIdField;
import static org.camunda.optimize.service.util.DecisionVariableHelper.getVariableMultivalueFields;
import static org.camunda.optimize.service.util.DecisionVariableHelper.getVariableValueFieldForType;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

public class RawDecisionDataCommand extends DecisionReportCommand<RawDataDecisionReportResultDto> {
  public static final String INPUT_VARIABLE_PREFIX = "inputVariable:";
  public static final String OUTPUT_VARIABLE_PREFIX = "outputVariable:";

  private static final Long RAW_DATA_LIMIT = 1_000L;

  private final RawDecisionDataResultDtoMapper rawDataSingleReportResultDtoMapper =
    new RawDecisionDataResultDtoMapper(RAW_DATA_LIMIT);

  public RawDataDecisionReportResultDto evaluate() {
    logger.debug(
      "Evaluating raw data report for decision definition key [{}] and version [{}]",
      getDecisionReportData().getDecisionDefinitionKey(),
      getDecisionReportData().getDecisionDefinitionVersion()
    );

    final DecisionReportDataDto decisionReportData = getDecisionReportData();
    final BoolQueryBuilder query = setupBaseQuery(
      getDecisionReportData().getDecisionDefinitionKey(),
      getDecisionReportData().getDecisionDefinitionVersion()
    );
    queryFilterEnhancer.addFilterToQuery(query, getDecisionReportData().getFilter());

    final SearchRequestBuilder searchRequestBuilder = esclient
      .prepareSearch(getOptimizeIndexAliasForType(ElasticsearchConstants.DECISION_INSTANCE_TYPE))
      .setTypes(ElasticsearchConstants.DECISION_INSTANCE_TYPE)
      .setQuery(query)
      .setSize(RAW_DATA_LIMIT.intValue());

    addSortingToQuery(decisionReportData, searchRequestBuilder);

    final SearchResponse scrollResp = searchRequestBuilder.get();
    return rawDataSingleReportResultDtoMapper.mapFrom(scrollResp, objectMapper);
  }

  private void addSortingToQuery(final DecisionReportDataDto decisionReportData,
                                 final SearchRequestBuilder searchRequestBuilder) {
    final Optional<SortingDto> customSorting = Optional.ofNullable(decisionReportData.getParameters())
      .flatMap(parameters -> Optional.ofNullable(parameters.getSorting()));
    final String sortByField = customSorting.flatMap(sorting -> Optional.ofNullable(sorting.getBy()))
      .orElse(DecisionInstanceType.EVALUATION_DATE_TIME);
    final SortOrder sortOrder = customSorting.flatMap(sorting -> Optional.ofNullable(sorting.getOrder()))
      .map(order -> SortOrder.valueOf(order.name()))
      .orElse(SortOrder.DESC);

    if (sortByField.startsWith(INPUT_VARIABLE_PREFIX)) {
      addSortByInputVariable(searchRequestBuilder, sortByField, sortOrder);
    } else if (sortByField.startsWith(OUTPUT_VARIABLE_PREFIX)) {
      addSortByOutputVariable(searchRequestBuilder, sortByField, sortOrder);
    } else {
      searchRequestBuilder.addSort(
        SortBuilders.fieldSort(sortByField).order(sortOrder)
          // this ensures the query doesn't fail on unknown properties but just ignores them
          // this is done to ensure consistent behavior compared to unknown variable names as ES doesn't fail there
          // https://www.elastic.co/guide/en/elasticsearch/reference/6.0/search-request-sort
          // .html#_ignoring_unmapped_fields
          .unmappedType("short")
      );
    }
  }

  private void addSortByInputVariable(final SearchRequestBuilder searchRequestBuilder,
                                      final String sortByField,
                                      final SortOrder sortOrder) {
    getVariableMultivalueFields()
      .forEach(type -> searchRequestBuilder.addSort(
        createSortByVariable(sortByField, sortOrder, INPUT_VARIABLE_PREFIX, INPUTS, type)
      ));

    // add default string field as last as it will always be present
    searchRequestBuilder.addSort(
      createSortByVariable(sortByField, sortOrder, INPUT_VARIABLE_PREFIX, INPUTS, VariableType.STRING)
    );
  }

  private void addSortByOutputVariable(final SearchRequestBuilder searchRequestBuilder,
                                       final String sortByField,
                                       final SortOrder sortOrder) {
    getVariableMultivalueFields()
      .forEach(type -> searchRequestBuilder.addSort(
        createSortByVariable(sortByField, sortOrder, OUTPUT_VARIABLE_PREFIX, OUTPUTS, type)
      ));

    // add default string field as last as it will always be present
    searchRequestBuilder.addSort(
      createSortByVariable(sortByField, sortOrder, OUTPUT_VARIABLE_PREFIX, OUTPUTS, VariableType.STRING)
    );
  }

  private FieldSortBuilder createSortByVariable(final String sortByField,
                                                final SortOrder sortOrder,
                                                final String prefix,
                                                final String variablePath,
                                                final VariableType type) {
    final String inputVariableId = sortByField.substring(prefix.length());
    final String variableValuePath = getVariableValueFieldForType(variablePath, type);
    final String variableIdPath = getVariableIdField(variablePath);

    return SortBuilders
      .fieldSort(variableValuePath)
      .setNestedPath(variablePath)
      .setNestedFilter(termQuery(variableIdPath, inputVariableId))
      .order(sortOrder);
  }

}

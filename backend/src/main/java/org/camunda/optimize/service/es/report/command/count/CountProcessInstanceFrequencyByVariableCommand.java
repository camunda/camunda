package org.camunda.optimize.service.es.report.command.count;

import org.camunda.optimize.dto.optimize.query.report.group.VariableGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.group.value.VariableGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.report.result.MapReportResultDto;
import org.camunda.optimize.service.es.report.command.ReportCommand;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;

import java.util.HashMap;
import java.util.Map;

import static org.camunda.optimize.service.util.VariableHelper.getNestedVariableNameFieldLabelForType;
import static org.camunda.optimize.service.util.VariableHelper.getNestedVariableValueFieldLabelForType;
import static org.camunda.optimize.service.util.VariableHelper.isDateType;
import static org.camunda.optimize.service.util.VariableHelper.variableTypeToFieldLabel;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

public class CountProcessInstanceFrequencyByVariableCommand extends ReportCommand<MapReportResultDto> {

  public static final String NESTED_AGGREGATION = "nested";
  public static final String VARIABLES_AGGREGATION = "variables";
  public static final String FILTERED_VARIABLES_AGGREGATION = "filteredVariables";

  @Override
  protected MapReportResultDto evaluate() {

    logger.debug("Evaluating count process instance frequency grouped by variable report " +
      "for process definition key [{}] and version [{}]",
      reportData.getProcessDefinitionKey(),
      reportData.getProcessDefinitionVersion());

    BoolQueryBuilder query = setupBaseQuery(
        reportData.getProcessDefinitionKey(),
        reportData.getProcessDefinitionVersion()
    );
    queryFilterEnhancer.addFilterToQuery(query, reportData.getFilter());

    VariableGroupByValueDto groupByVariable = ((VariableGroupByDto) reportData.getGroupBy()).getValue();

    SearchResponse response = esclient
      .prepareSearch(configurationService.getOptimizeIndex(configurationService.getProcessInstanceType()))
      .setTypes(configurationService.getProcessInstanceType())
      .setQuery(query)
      .setFetchSource(false)
      .setSize(0)
      .addAggregation(createAggregation(groupByVariable.getName(), groupByVariable.getType()))
      .get();

    MapReportResultDto mapResult = new MapReportResultDto();
    mapResult.setResult(processAggregations(response.getAggregations()));
    mapResult.setProcessInstanceCount(response.getHits().getTotalHits());
    return mapResult;
  }

  private AggregationBuilder createAggregation(String variableName, String variableType) {

    String path = variableTypeToFieldLabel(variableType);
    String nestedVariableNameFieldLabel = getNestedVariableNameFieldLabelForType(variableType);
    String nestedVariableValueFieldLabel = getNestedVariableValueFieldLabelForType(variableType);
    TermsAggregationBuilder collectVariableValueCount = AggregationBuilders
            .terms(VARIABLES_AGGREGATION)
            .size(Integer.MAX_VALUE)
            .field(nestedVariableValueFieldLabel);
    if (isDateType(variableType)) {
      collectVariableValueCount.format(configurationService.getOptimizeDateFormat());
    }
    return nested(NESTED_AGGREGATION, path)
      .subAggregation(
        filter(
          FILTERED_VARIABLES_AGGREGATION,
          boolQuery()
            .must(
              termQuery(nestedVariableNameFieldLabel, variableName)
            )
        )
          .subAggregation(collectVariableValueCount)

      );
  }

  private Map<String, Long> processAggregations(Aggregations aggregations) {
    Nested nested = aggregations.get(NESTED_AGGREGATION);
    Filter filteredVariables = nested.getAggregations().get(FILTERED_VARIABLES_AGGREGATION);
    Terms variableTerms = filteredVariables.getAggregations().get(VARIABLES_AGGREGATION);
    Map<String, Long> result = new HashMap<>();
    for (Terms.Bucket b : variableTerms.getBuckets()) {
      result.put(b.getKeyAsString(), b.getDocCount());
    }
    return result;
  }

}

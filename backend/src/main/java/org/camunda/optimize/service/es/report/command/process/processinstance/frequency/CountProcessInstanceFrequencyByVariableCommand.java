package org.camunda.optimize.service.es.report.command.process.processinstance.frequency;

import org.camunda.optimize.dto.optimize.query.report.VariableType;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.VariableGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.VariableGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportMapResultDto;
import org.camunda.optimize.service.es.report.command.process.ProcessReportCommand;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
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

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableNameFieldLabelForType;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableValueFieldLabelForType;
import static org.camunda.optimize.service.util.ProcessVariableHelper.variableTypeToFieldLabel;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

public class CountProcessInstanceFrequencyByVariableCommand extends ProcessReportCommand<ProcessReportMapResultDto> {

  public static final String NESTED_AGGREGATION = "nested";
  public static final String VARIABLES_AGGREGATION = "variables";
  public static final String FILTERED_VARIABLES_AGGREGATION = "filteredVariables";

  @Override
  protected ProcessReportMapResultDto evaluate() {

    final ProcessReportDataDto processReportData = getProcessReportData();
    logger.debug("Evaluating count process instance frequency grouped by variable report " +
      "for process definition key [{}] and version [{}]",
                 processReportData.getProcessDefinitionKey(),
                 processReportData.getProcessDefinitionVersion());

    BoolQueryBuilder query = setupBaseQuery(
      processReportData.getProcessDefinitionKey(),
      processReportData.getProcessDefinitionVersion()
    );
    queryFilterEnhancer.addFilterToQuery(query, processReportData.getFilter());

    VariableGroupByValueDto groupByVariable = ((VariableGroupByDto) processReportData.getGroupBy()).getValue();

    SearchResponse response = esclient
      .prepareSearch(getOptimizeIndexAliasForType(ElasticsearchConstants.PROC_INSTANCE_TYPE))
      .setTypes(ElasticsearchConstants.PROC_INSTANCE_TYPE)
      .setQuery(query)
      .setFetchSource(false)
      .setSize(0)
      .addAggregation(createAggregation(groupByVariable.getName(), groupByVariable.getType()))
      .get();

    ProcessReportMapResultDto mapResult = new ProcessReportMapResultDto();
    mapResult.setResult(processAggregations(response.getAggregations()));
    mapResult.setProcessInstanceCount(response.getHits().getTotalHits());
    return mapResult;
  }

  private AggregationBuilder createAggregation(String variableName, VariableType variableType) {

    String path = variableTypeToFieldLabel(variableType);
    String nestedVariableNameFieldLabel = getNestedVariableNameFieldLabelForType(variableType);
    String nestedVariableValueFieldLabel = getNestedVariableValueFieldLabelForType(variableType);
    TermsAggregationBuilder collectVariableValueCount = AggregationBuilders
            .terms(VARIABLES_AGGREGATION)
            .size(Integer.MAX_VALUE)
            .field(nestedVariableValueFieldLabel);

    if (VariableType.DATE.equals(variableType)) {
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

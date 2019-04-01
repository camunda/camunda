package org.camunda.optimize.service.es.report.command.process.processinstance.frequency;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.VariableGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.VariableGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.es.report.command.process.ProcessReportCommand;
import org.camunda.optimize.service.es.report.command.util.MapResultSortingUtility;
import org.camunda.optimize.service.es.report.result.process.SingleProcessMapReportResult;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableNameFieldLabelForType;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableValueFieldLabelForType;
import static org.camunda.optimize.service.util.ProcessVariableHelper.variableTypeToFieldLabel;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

public class CountProcessInstanceFrequencyByVariableCommand extends ProcessReportCommand<SingleProcessMapReportResult> {

  private static final String NESTED_AGGREGATION = "nested";
  private static final String VARIABLES_AGGREGATION = "variables";
  private static final String FILTERED_VARIABLES_AGGREGATION = "filteredVariables";

  @Override
  protected SingleProcessMapReportResult evaluate() {

    final ProcessReportDataDto processReportData = getReportData();
    logger.debug(
      "Evaluating count process instance frequency grouped by variable report " +
        "for process definition key [{}] and version [{}]",
      processReportData.getProcessDefinitionKey(),
      processReportData.getProcessDefinitionVersion()
    );

    BoolQueryBuilder query = setupBaseQuery(processReportData);

    VariableGroupByValueDto groupByVariable = ((VariableGroupByDto) processReportData.getGroupBy()).getValue();

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .fetchSource(false)
      .aggregation(createAggregation(groupByVariable.getName(), groupByVariable.getType()))
      .size(0);
    SearchRequest searchRequest =
      new SearchRequest(getOptimizeIndexAliasForType(PROC_INSTANCE_TYPE))
        .types(PROC_INSTANCE_TYPE)
        .source(searchSourceBuilder);

    SearchResponse response;
    try {
      response = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason =
        String.format(
          "Could not evaluate count process instance frequency grouped by variable report " +
            "for process definition key [%s] and version [%s]",
          processReportData.getProcessDefinitionKey(),
          processReportData.getProcessDefinitionVersion()
        );
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    ProcessReportMapResultDto mapResultDto = new ProcessReportMapResultDto();
    mapResultDto.setData(processAggregations(response.getAggregations()));
    mapResultDto.setProcessInstanceCount(response.getHits().getTotalHits());
    return new SingleProcessMapReportResult(mapResultDto, reportDefinition);
  }

  @Override
  protected void sortResultData(final SingleProcessMapReportResult evaluationResult) {
    ((ProcessReportDataDto) getReportData()).getParameters().getSorting().ifPresent(
      sorting -> MapResultSortingUtility.sortResultData(sorting, evaluationResult)
    );
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
      collectVariableValueCount.format(OPTIMIZE_DATE_FORMAT);
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

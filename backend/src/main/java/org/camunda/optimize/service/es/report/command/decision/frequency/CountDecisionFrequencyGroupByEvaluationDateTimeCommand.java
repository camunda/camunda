package org.camunda.optimize.service.es.report.command.decision.frequency;

import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByEvaluationDateTimeDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.value.DecisionGroupByEvaluationDateTimeValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.DecisionReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.service.es.report.command.decision.DecisionReportCommand;
import org.camunda.optimize.service.es.schema.type.DecisionInstanceType;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.joda.time.DateTime;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.camunda.optimize.service.es.report.command.util.ReportUtil.getDateHistogramInterval;
import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_TYPE;

public class CountDecisionFrequencyGroupByEvaluationDateTimeCommand
  extends DecisionReportCommand<DecisionReportMapResultDto> {

  private static final String DATE_HISTOGRAM_AGGREGATION = "dateIntervalGrouping";

  @Override
  protected DecisionReportMapResultDto evaluate() throws OptimizeException {

    final DecisionReportDataDto reportData = getDecisionReportData();
    logger.debug(
      "Evaluating count decision instance frequency grouped by evaluation date report " +
        "for decision definition key [{}] and version [{}]",
      reportData.getDecisionDefinitionKey(),
      reportData.getDecisionDefinitionVersion()
    );

    BoolQueryBuilder query = setupBaseQuery(
      reportData.getDecisionDefinitionKey(),
      reportData.getDecisionDefinitionVersion()
    );
    queryFilterEnhancer.addFilterToQuery(query, reportData.getFilter());

    DecisionGroupByEvaluationDateTimeValueDto groupBy = ((DecisionGroupByEvaluationDateTimeDto) reportData.getGroupBy())
      .getValue();

    SearchResponse response = esclient
      .prepareSearch(getOptimizeIndexAliasForType(DECISION_INSTANCE_TYPE))
      .setTypes(DECISION_INSTANCE_TYPE)
      .setQuery(query)
      .setFetchSource(false)
      .setSize(0)
      .addAggregation(createAggregation(groupBy.getUnit()))
      .get();

    DecisionReportMapResultDto mapResult = new DecisionReportMapResultDto();
    mapResult.setResult(mapAggregationsToMapResult(response.getAggregations()));
    mapResult.setDecisionInstanceCount(response.getHits().getTotalHits());
    return mapResult;
  }

  private AggregationBuilder createAggregation(final GroupByDateUnit unit) throws OptimizeException {
    DateHistogramInterval interval = getDateHistogramInterval(unit);
    return AggregationBuilders
      .dateHistogram(DATE_HISTOGRAM_AGGREGATION)
      .order(BucketOrder.key(false))
      .field(DecisionInstanceType.EVALUATION_DATE_TIME)
      .dateHistogramInterval(interval);
  }

  private Map<String, Long> mapAggregationsToMapResult(final Aggregations aggregations) {
    Histogram agg = aggregations.get(DATE_HISTOGRAM_AGGREGATION);

    Map<String, Long> result = new LinkedHashMap<>();
    for (Histogram.Bucket entry : agg.getBuckets()) {
      DateTime key = (DateTime) entry.getKey();
      long docCount = entry.getDocCount();
      String formattedDate = key.toString(configurationService.getOptimizeDateFormat());
      result.put(formattedDate, docCount);
    }
    return result;
  }

}

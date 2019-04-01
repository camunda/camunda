package org.camunda.optimize.service.es.report.command.process.flownode.duration;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.AggregationResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.ProcessDurationReportMapResultDto;
import org.camunda.optimize.service.es.report.command.process.FlowNodeDurationGroupingCommand;
import org.camunda.optimize.service.es.report.command.util.MapResultSortingUtility;
import org.camunda.optimize.service.es.report.result.process.SingleProcessMapDurationReportResult;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.ValidationHelper;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.percentiles.tdigest.ParsedTDigestPercentiles;
import org.elasticsearch.search.aggregations.metrics.stats.ParsedStats;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.camunda.optimize.service.es.report.command.util.ElasticsearchAggregationResultMappingUtil.mapToLong;
import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.ACTIVITY_DURATION;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.ACTIVITY_ID;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.ACTIVITY_TYPE;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.EVENTS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.elasticsearch.search.aggregations.AggregationBuilders.percentiles;
import static org.elasticsearch.search.aggregations.AggregationBuilders.stats;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

public class FlowNodeDurationByFlowNodeCommand extends FlowNodeDurationGroupingCommand {

  private static final String MI_BODY = "multiInstanceBody";
  private static final String EVENTS_AGGREGATION = "events";
  private static final String FILTERED_EVENTS_AGGREGATION = "filteredEvents";
  private static final String ACTIVITY_ID_TERMS_AGGREGATION = "activities";
  private static final String STATS_DURATION_AGGREGATION = "statsAggregatedDuration";
  private static final String MEDIAN_DURATION_AGGREGATION = "medianAggregatedDuration";

  @Override
  protected SingleProcessMapDurationReportResult evaluate() {

    final ProcessReportDataDto processReportData = getReportData();
    logger.debug(
      "Evaluating flow node duration grouped by flow node report " +
        "for process definition key [{}] and version [{}]",
      processReportData.getProcessDefinitionKey(),
      processReportData.getProcessDefinitionVersion()
    );

    BoolQueryBuilder query = setupBaseQuery(processReportData);

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .fetchSource(false)
      .aggregation(createAggregation())
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
          "Could not evaluate flow node duration grouped by " +
            "flow node report for process definition key [%s] and version [%s]",
          processReportData.getProcessDefinitionKey(),
          processReportData.getProcessDefinitionVersion()
        );
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    Map<String, AggregationResultDto> resultMap = processAggregations(response.getAggregations());
    ProcessDurationReportMapResultDto resultDto =
      new ProcessDurationReportMapResultDto();
    resultDto.setData(resultMap);
    resultDto.setProcessInstanceCount(response.getHits().getTotalHits());
    return new SingleProcessMapDurationReportResult(resultDto, reportDefinition);
  }

  @Override
  protected void sortResultData(final SingleProcessMapDurationReportResult evaluationResult) {
    ((ProcessReportDataDto) getReportData()).getParameters().getSorting().ifPresent(
      sorting -> MapResultSortingUtility.sortResultData(sorting, evaluationResult)
    );
  }

  private AggregationBuilder createAggregation() {
    return
      nested(EVENTS, EVENTS_AGGREGATION)
        .subAggregation(
          filter(
            FILTERED_EVENTS_AGGREGATION,
            boolQuery()
              .mustNot(
                termQuery(EVENTS + "." + ACTIVITY_TYPE, MI_BODY)
              )
              .must(existsQuery(EVENTS + "." + ACTIVITY_DURATION))
          )
            .subAggregation(
              terms(ACTIVITY_ID_TERMS_AGGREGATION)
                .size(Integer.MAX_VALUE)
                .field(EVENTS + "." + ACTIVITY_ID)
                .subAggregation(
                  stats(STATS_DURATION_AGGREGATION)
                    .field(EVENTS + "." + ACTIVITY_DURATION)
                )
                .subAggregation(
                  percentiles(MEDIAN_DURATION_AGGREGATION)
                    .percentiles(50)
                    .field(EVENTS + "." + ACTIVITY_DURATION)
                )
            )
        );
  }

  private Map<String, AggregationResultDto> processAggregations(Aggregations aggregations) {
    ValidationHelper.ensureNotNull("aggregations", aggregations);
    Nested activities = aggregations.get(EVENTS_AGGREGATION);
    Filter filteredActivities = activities.getAggregations().get(FILTERED_EVENTS_AGGREGATION);
    Terms terms = filteredActivities.getAggregations().get(ACTIVITY_ID_TERMS_AGGREGATION);
    Map<String, AggregationResultDto> result = new HashMap<>();
    for (Terms.Bucket b : terms.getBuckets()) {
      ParsedStats statsAggregation = b.getAggregations().get(STATS_DURATION_AGGREGATION);
      ParsedTDigestPercentiles medianAggregation = b.getAggregations().get(MEDIAN_DURATION_AGGREGATION);

      AggregationResultDto aggregationResultDto = new AggregationResultDto(
        mapToLong(statsAggregation.getMin()),
        mapToLong(statsAggregation.getMax()),
        mapToLong(statsAggregation.getAvg()),
        mapToLong(medianAggregation)
      );

      result.put(b.getKeyAsString(), aggregationResultDto);
    }
    return result;
  }

}

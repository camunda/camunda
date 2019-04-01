package org.camunda.optimize.service.es.report.command.process.user_task.duration;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.AggregationResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.ProcessDurationReportMapResultDto;
import org.camunda.optimize.service.es.report.command.process.UserTaskGroupingCommand;
import org.camunda.optimize.service.es.report.command.util.MapResultSortingUtility;
import org.camunda.optimize.service.es.report.result.process.SingleProcessMapDurationReportResult;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.ValidationHelper;
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
import org.elasticsearch.search.aggregations.metrics.percentiles.tdigest.ParsedTDigestPercentiles;
import org.elasticsearch.search.aggregations.metrics.stats.ParsedStats;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.camunda.optimize.service.es.report.command.util.ElasticsearchAggregationResultMappingUtil.mapToLong;
import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_TASKS;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_TASK_ACTIVITY_ID;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.elasticsearch.search.aggregations.AggregationBuilders.percentiles;
import static org.elasticsearch.search.aggregations.AggregationBuilders.stats;

public abstract class AbstractUserTaskDurationByUserTaskCommand extends UserTaskGroupingCommand {

  private static final String USER_TASK_ID_TERMS_AGGREGATION = "tasks";
  private static final String USER_TASKS_AGGREGATION = "userTasks";
  private static final String FILTERED_USER_TASKS_AGGREGATION = "filteredUserTasks";
  private static final String STATS_DURATION_AGGREGATION = "statsAggregatedDuration";
  private static final String MEDIAN_DURATION_AGGREGATION = "medianAggregatedDuration";

  @Override
  protected SingleProcessMapDurationReportResult evaluate() {

    final ProcessReportDataDto processReportData = getReportData();
    logger.debug(
      "Evaluating user task total duration report for process definition key [{}] and version [{}]",
      processReportData.getProcessDefinitionKey(),
      processReportData.getProcessDefinitionVersion()
    );

    final BoolQueryBuilder query = setupBaseQuery(processReportData);
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .fetchSource(false)
      .aggregation(createAggregation())
      .size(0);
    final SearchRequest searchRequest = new SearchRequest(getOptimizeIndexAliasForType(PROC_INSTANCE_TYPE))
      .types(PROC_INSTANCE_TYPE)
      .source(searchSourceBuilder);

    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      final Map<String, AggregationResultDto> resultMap = processAggregations(response.getAggregations());
      final ProcessDurationReportMapResultDto resultDto = new ProcessDurationReportMapResultDto();
      resultDto.setData(resultMap);
      resultDto.setProcessInstanceCount(response.getHits().getTotalHits());
      return new SingleProcessMapDurationReportResult(resultDto, reportDefinition);
    } catch (IOException e) {
      final String reason = String.format(
        "Could not evaluate user task total duration for process definition key [%s] and version [%s]",
        processReportData.getProcessDefinitionKey(),
        processReportData.getProcessDefinitionVersion()
      );
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
  }

  @Override
  protected void sortResultData(final SingleProcessMapDurationReportResult evaluationResult) {
    ((ProcessReportDataDto) getReportData()).getParameters().getSorting().ifPresent(
      sorting -> MapResultSortingUtility.sortResultData(sorting, evaluationResult)
    );
  }

  private AggregationBuilder createAggregation() {
    return nested(USER_TASKS, USER_TASKS_AGGREGATION)
      .subAggregation(
        filter(
          FILTERED_USER_TASKS_AGGREGATION,
          boolQuery()
            .must(existsQuery(USER_TASKS + "." + getDurationFieldName()))
        )
          .subAggregation(
            AggregationBuilders
              .terms(USER_TASK_ID_TERMS_AGGREGATION)
              .size(Integer.MAX_VALUE)
              .field(USER_TASKS + "." + USER_TASK_ACTIVITY_ID)
              .subAggregation(
                stats(STATS_DURATION_AGGREGATION)
                  .field(USER_TASKS + "." + getDurationFieldName())
              )
              .subAggregation(
                percentiles(MEDIAN_DURATION_AGGREGATION)
                  .percentiles(50)
                  .field(USER_TASKS + "." + getDurationFieldName())
              )
          )
      );
  }

  protected abstract String getDurationFieldName();

  private Map<String, AggregationResultDto> processAggregations(final Aggregations aggregations) {
    ValidationHelper.ensureNotNull("aggregations", aggregations);
    final Nested userTasks = aggregations.get(USER_TASKS_AGGREGATION);
    final Filter filteredUserTasks = userTasks.getAggregations().get(FILTERED_USER_TASKS_AGGREGATION);
    final Terms byTaskIdAggregation = filteredUserTasks.getAggregations().get(USER_TASK_ID_TERMS_AGGREGATION);
    final Map<String, AggregationResultDto> result = new HashMap<>();
    for (Terms.Bucket b : byTaskIdAggregation.getBuckets()) {
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

package org.camunda.optimize.service.es.report.command.process.user_task.duration;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportMapResultDto;
import org.camunda.optimize.service.es.report.command.process.UserTaskGroupingCommand;
import org.camunda.optimize.service.es.report.result.process.SingleProcessMapReportResult;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.ValidationHelper;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.support.ValuesSourceAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_TASKS;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_TASK_ACTIVITY_ID;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

public abstract class AbstractUserTaskDurationByUserTaskCommand<T extends Aggregation> extends UserTaskGroupingCommand {

  private static final String USER_TASK_ID_TERMS_AGGREGATION = "tasks";
  private static final String USER_TASKS_AGGREGATION = "userTasks";
  private static final String FILTERED_USER_TASKS_AGGREGATION = "filteredUserTasks";
  private static final String DURATION_AGGREGATION = "durationAggregation";

  @Override
  protected SingleProcessMapReportResult evaluate() {

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
      final Map<String, Long> resultMap = processAggregations(response.getAggregations());
      final ProcessReportMapResultDto resultDto = new ProcessReportMapResultDto();
      resultDto.setResult(resultMap);
      return new SingleProcessMapReportResult(resultDto);
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

  private AggregationBuilder createAggregation() {
    return nested(USER_TASKS, USER_TASKS_AGGREGATION)
      .subAggregation(
        filter(
          FILTERED_USER_TASKS_AGGREGATION,
          boolQuery()
            .must(existsQuery(USER_TASKS + "." + getDurationFieldName()))
        )
          .subAggregation(AggregationBuilders
                            .terms(USER_TASK_ID_TERMS_AGGREGATION)
                            .size(Integer.MAX_VALUE)
                            .field(USER_TASKS + "." + USER_TASK_ACTIVITY_ID)
                            .subAggregation(
                              getDurationAggregationBuilder(DURATION_AGGREGATION)
                                .field(USER_TASKS + "." + getDurationFieldName())
                            )
          )
      );
  }

  protected abstract String getDurationFieldName();

  protected abstract ValuesSourceAggregationBuilder<?, ?> getDurationAggregationBuilder(String aggregationName);

  protected abstract Long mapDurationByTaskIdAggregationResult(T aggregation);

  private Map<String, Long> processAggregations(final Aggregations aggregations) {
    ValidationHelper.ensureNotNull("aggregations", aggregations);
    final Nested userTasks = aggregations.get(USER_TASKS_AGGREGATION);
    final Filter filteredUserTasks = userTasks.getAggregations().get(FILTERED_USER_TASKS_AGGREGATION);
    final Terms byTaskIdAggregation = filteredUserTasks.getAggregations().get(USER_TASK_ID_TERMS_AGGREGATION);
    final Map<String, Long> result = new HashMap<>();
    for (Terms.Bucket b : byTaskIdAggregation.getBuckets()) {
      Long roundedDuration = mapDurationByTaskIdAggregationResult(b.getAggregations().get(DURATION_AGGREGATION));
      result.put(b.getKeyAsString(), roundedDuration);
    }
    return result;
  }

}

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
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.support.ValuesSourceAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.UserTaskInstanceType.ACTIVITY_ID;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.USER_TASK_INSTANCE_TYPE;

public abstract class AbstractUserTaskDurationByUserTaskCommand<T extends Aggregation> extends UserTaskGroupingCommand {

  private static final String TASK_ID_TERMS_AGGREGATION = "tasks";
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
    final SearchRequest searchRequest = new SearchRequest(getOptimizeIndexAliasForType(USER_TASK_INSTANCE_TYPE))
      .types(USER_TASK_INSTANCE_TYPE)
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
    return AggregationBuilders
      .terms(TASK_ID_TERMS_AGGREGATION)
      .size(Integer.MAX_VALUE)
      .field(ACTIVITY_ID)
      .subAggregation(
        getDurationAggregationBuilder(DURATION_AGGREGATION)
          .field(getDurationFieldName())
      );
  }

  protected abstract String getDurationFieldName();

  protected abstract ValuesSourceAggregationBuilder<?, ?> getDurationAggregationBuilder(String aggregationName);

  protected abstract Long mapDurationByTaskIdAggregationResult(T aggregation);

  private Map<String, Long> processAggregations(final Aggregations aggregations) {
    ValidationHelper.ensureNotNull("aggregations", aggregations);
    final Terms byTaskIdAggregation = aggregations.get(TASK_ID_TERMS_AGGREGATION);
    final Map<String, Long> result = new HashMap<>();
    for (Terms.Bucket b : byTaskIdAggregation.getBuckets()) {
      Long roundedDuration = mapDurationByTaskIdAggregationResult(b.getAggregations().get(DURATION_AGGREGATION));
      result.put(b.getKeyAsString(), roundedDuration);
    }
    return result;
  }

}

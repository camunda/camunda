package org.camunda.optimize.service.es.report.command.flownode.duration;

import org.camunda.optimize.dto.optimize.query.report.result.MapReportResultDto;
import org.camunda.optimize.service.es.report.command.FlowNodeGroupingCommand;
import org.camunda.optimize.service.util.ValidationHelper;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;

import java.util.HashMap;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.ACTIVITY_ID;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.ACTIVITY_TYPE;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.DURATION;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.EVENTS;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

public abstract class AbstractFlowNodeDurationByFlowNodeCommand<T extends Aggregation> extends FlowNodeGroupingCommand {

  private static final String MI_BODY = "multiInstanceBody";
  private static final String EVENTS_AGGREGATION = "events";
  private static final String FILTERED_EVENTS_AGGREGATION = "filteredEvents";
  private static final String ACTIVITY_ID_TERMS_AGGREGATION = "activities";
  private static final String DURATION_AGGREGATION = "aggregatedDuration";

  @Override
  protected MapReportResultDto evaluate() {

    logger.debug("Evaluating flow node duration grouped by flow node report " +
      "for process definition key [{}] and version [{}]",
      reportData.getProcessDefinitionKey(),
      reportData.getProcessDefinitionVersion());

    BoolQueryBuilder query = setupBaseQuery(
        reportData.getProcessDefinitionKey(),
        reportData.getProcessDefinitionVersion()
    );

    queryFilterEnhancer.addFilterToQuery(query, reportData.getFilter());

    SearchResponse response = esclient
      .prepareSearch(configurationService.getOptimizeIndex(configurationService.getProcessInstanceType()))
      .setTypes(configurationService.getProcessInstanceType())
      .setQuery(query)
      .setFetchSource(false)
      .setSize(0)
      .addAggregation(createAggregation())
      .get();

    Map<String, Long> resultMap = processAggregations(response.getAggregations());
    MapReportResultDto resultDto =
      new MapReportResultDto();
    resultDto.setResult(resultMap);
    resultDto.setProcessInstanceCount(response.getHits().getTotalHits());
    return resultDto;
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
          )
            .subAggregation(AggregationBuilders
              .terms(ACTIVITY_ID_TERMS_AGGREGATION)
              .size(Integer.MAX_VALUE)
              .field(EVENTS + "." + ACTIVITY_ID)
              .subAggregation(
                addOperation(DURATION_AGGREGATION, EVENTS + "." + DURATION)
              )
            )
        );
  }

  protected abstract AggregationBuilder addOperation(String aggregationName, String field);

  private Map<String, Long> processAggregations(Aggregations aggregations) {
    ValidationHelper.ensureNotNull("aggregations", aggregations);
    Nested activities = aggregations.get(EVENTS_AGGREGATION);
    Filter filteredActivities = activities.getAggregations().get(FILTERED_EVENTS_AGGREGATION);
    Terms terms = filteredActivities.getAggregations().get(ACTIVITY_ID_TERMS_AGGREGATION);
    Map<String, Long> result = new HashMap<>();
    for (Terms.Bucket b : terms.getBuckets()) {
      Long roundedDuration =
        processOperationAggregation(b.getAggregations().get(DURATION_AGGREGATION));
      result.put(b.getKeyAsString(), roundedDuration);
    }
    return result;
  }

  protected abstract Long processOperationAggregation(T aggregation);

}

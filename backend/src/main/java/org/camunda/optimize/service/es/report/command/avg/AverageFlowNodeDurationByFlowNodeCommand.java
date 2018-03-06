package org.camunda.optimize.service.es.report.command.avg;

import org.camunda.optimize.dto.optimize.query.report.result.MapReportResultDto;
import org.camunda.optimize.service.es.report.command.FlowNodeGroupingCommand;
import org.camunda.optimize.service.util.ValidationHelper;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.avg.InternalAvg;

import java.util.HashMap;
import java.util.Map;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.avg;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

public class AverageFlowNodeDurationByFlowNodeCommand extends FlowNodeGroupingCommand {

  private static final String MI_BODY = "multiInstanceBody";

  @Override
  protected MapReportResultDto evaluate() {

    logger.debug("Evaluating average flow node duration grouped by flow node report " +
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
      nested("events", "events")
        .subAggregation(
          filter(
            "filteredEvents",
            boolQuery()
              .mustNot(
                termQuery("events.activityType", MI_BODY)
              )
          )
            .subAggregation(AggregationBuilders
              .terms("activities")
              .size(Integer.MAX_VALUE)
              .field("events.activityId")
              .subAggregation(
                avg("aggregatedDuration")
                  .field("events.durationInMs")
              )
            )
        );
  }

  private Map<String, Long> processAggregations(Aggregations aggregations) {
    ValidationHelper.ensureNotNull("aggregations", aggregations);
    Nested activities = aggregations.get("events");
    Filter filteredActivities = activities.getAggregations().get("filteredEvents");
    Terms terms = filteredActivities.getAggregations().get("activities");
    Map<String, Long> result = new HashMap<>();
    for (Terms.Bucket b : terms.getBuckets()) {
      InternalAvg averageDuration = b.getAggregations().get("aggregatedDuration");
      long roundedDuration = Math.round(averageDuration.getValue());
      result.put(b.getKeyAsString(), roundedDuration);
    }
    return result;
  }

}

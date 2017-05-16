package org.camunda.optimize.service.es.reader;

import org.camunda.optimize.dto.optimize.HeatMapResponseDto;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.avg.InternalAvg;
import org.elasticsearch.search.aggregations.metrics.cardinality.Cardinality;
import org.springframework.stereotype.Component;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.*;

@Component
public class DurationHeatMapReader extends HeatMapReader {

  void processAggregations(HeatMapResponseDto result, SearchRequestBuilder srb) {
    Aggregations aggregations = getTermsWithAggregation(srb);
    Nested activities = aggregations.get("events");
    Filter filteredActivities = activities.getAggregations().get("filteredEvents");
    Terms terms = filteredActivities.getAggregations().get("activities");
    for (Terms.Bucket b : terms.getBuckets()) {
      InternalAvg averageDuration = b.getAggregations().get("aggregatedDuration");
      long roundedDuration = Math.round(averageDuration.getValue());
      result.getFlowNodes().put(b.getKeyAsString(), roundedDuration);
    }

    Cardinality pi = aggregations.get("pi");
    result.setPiCount(pi.getValue());
  }

  private Aggregations getTermsWithAggregation(SearchRequestBuilder srb) {
    SearchResponse sr = srb
      .addAggregation(
        nested("events", "events")
        .subAggregation(
          filter(
            "filteredEvents",
            boolQuery()
              .mustNot(
                termQuery("events.activityType", MI_BODY)
              )
          )
          .subAggregation(
            terms("activities")
            .size(Integer.MAX_VALUE)
            .field("events.activityId")
            .subAggregation(
              avg("aggregatedDuration")
              .field("events.durationInMs")
            )
          )
        )
      )
      .addAggregation(
        cardinality("pi")
        .field("processInstanceId")
      )
      .get();

    return sr.getAggregations();
  }
}

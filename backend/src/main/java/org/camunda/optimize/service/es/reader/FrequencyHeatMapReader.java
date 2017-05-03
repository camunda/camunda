package org.camunda.optimize.service.es.reader;

import org.camunda.optimize.dto.optimize.HeatMapResponseDto;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.cardinality.Cardinality;
import org.springframework.stereotype.Component;

import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

/**
 * @author Askar Akhmerov
 */
@Component
public class FrequencyHeatMapReader extends HeatMapReader {

  void processAggregations(HeatMapResponseDto result, SearchRequestBuilder srb) {
    Aggregations aggregations = getTermsWithAggregation(srb);
    Nested activities = aggregations.get("events");
    Filter filteredActivities = activities.getAggregations().get("filteredEvents");
    Terms terms = filteredActivities.getAggregations().get("activities");
    for (Terms.Bucket b : terms.getBuckets()) {
      result.getFlowNodes().put(b.getKeyAsString(), b.getDocCount());
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
          .subAggregation(AggregationBuilders
            .terms("activities")
            .size(Integer.MAX_VALUE)
            .field("events.activityId")
          )
        )
      )
      .addAggregation(AggregationBuilders
        .cardinality("pi")
        .field("processInstanceId")
      )
      .get();

    return sr.getAggregations();
  }

}

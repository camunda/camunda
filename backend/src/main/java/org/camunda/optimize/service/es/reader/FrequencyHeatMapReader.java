package org.camunda.optimize.service.es.reader;

import org.camunda.optimize.dto.optimize.HeatMapResponseDto;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.cardinality.Cardinality;
import org.springframework.stereotype.Component;

/**
 * @author Askar Akhmerov
 */
@Component
public class FrequencyHeatMapReader extends HeatMapReader {

  void processAggregations(HeatMapResponseDto result, SearchRequestBuilder srb) {
    Aggregations aggregations = getTermsWithAggregation(srb);
    Terms activities = aggregations.get("activities");
    for (Terms.Bucket b : activities.getBuckets()) {
      result.getFlowNodes().put(b.getKeyAsString(), b.getDocCount());
    }

    Cardinality pi = aggregations.get("pi");
    result.setPiCount(pi.getValue());
  }

  private Aggregations getTermsWithAggregation(SearchRequestBuilder srb) {
    SearchResponse sr = srb
      .addAggregation(AggregationBuilders
        .terms("activities")
        .size(Integer.MAX_VALUE)
        .field("activityId")
      )
      .addAggregation(AggregationBuilders
        .cardinality("pi")
        .field("processInstanceId")
      )
      .get();

    return sr.getAggregations();
  }

}

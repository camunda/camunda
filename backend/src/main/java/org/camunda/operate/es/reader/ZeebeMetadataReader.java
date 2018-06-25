package org.camunda.operate.es.reader;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.LongTerms;
import org.elasticsearch.search.aggregations.metrics.cardinality.Cardinality;
import org.elasticsearch.search.aggregations.metrics.max.Max;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;


@Component
public class ZeebeMetadataReader {

  private Logger logger = LoggerFactory.getLogger(ZeebeMetadataReader.class);

  @Autowired
  protected TransportClient esClient;

  @Autowired
  @Qualifier("esObjectMapper")
  private ObjectMapper objectMapper;

  /**
   * Gets the maximum position value for each partition id. Searches in all available indices.
   * @return
   */
  public Map<Integer, Long> getPositionPerPartitionMap() {
    Map<Integer, Long> result = new HashMap<>();

    final String partitionsAggName = "partitions";
    final String maxPositionAggName = "maxPosition";

    // https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations-metrics-cardinality-aggregation.html
    int partitionsCount = (int)partitionsCount();
    partitionsCount = (int)(partitionsCount * 1.1 + 1); // + 10%

    SearchResponse response =
      esClient
        .prepareSearch()
        .addAggregation(AggregationBuilders
          .terms(partitionsAggName)
          .field("partitionId")
          .size(partitionsCount)
          .subAggregation(AggregationBuilders
            .max("maxPosition")
            .field("position")))
        .setFetchSource(false)
        .setSize(0)
        .get();
    final List<LongTerms.Bucket> buckets =
      ((LongTerms) response
        .getAggregations()
          .get(partitionsAggName))
          .getBuckets();
    for (LongTerms.Bucket bucket: buckets) {
      final double maxPosition =
        ((Max) bucket
        .getAggregations()
          .get(maxPositionAggName))
          .getValue();
      result.put(Integer.valueOf(bucket.getKeyAsString()), (long)maxPosition);
    }
    return result;
  }

  private long partitionsCount() {
    String partitionCountAggName = "partitionCount";

    SearchResponse response =
      esClient
        .prepareSearch()
        .addAggregation(AggregationBuilders
          .cardinality(partitionCountAggName)
          .field("partitionId")
          .subAggregation(AggregationBuilders
            .max("maxPosition")
            .field("position")))
        .setFetchSource(false)
        .setSize(0)
        .get();

    return ((Cardinality)response.getAggregations().get("partitionCount")).getValue();
  }

}

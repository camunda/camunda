package org.camunda.optimize.service.es.reader;

import org.camunda.optimize.dto.optimize.HeatMapQueryDto;
import org.camunda.optimize.service.es.mapping.DateFilterHelper;
import org.camunda.optimize.service.util.ConfigurationService;
import org.camunda.optimize.service.util.ValidationHelper;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Askar Akhmerov
 */
@Component
public class HeatMapReader {
  @Autowired
  private TransportClient esclient;
  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private DateFilterHelper dateFilterHelper;

  public TransportClient getEsclient() {
    return esclient;
  }

  public void setEsclient(TransportClient esclient) {
    this.esclient = esclient;
  }

  public Map<String, Long> getHeatMap(String processDefinitionId) {
    Map<String, Long> result = new HashMap<>();

    SearchRequestBuilder srb = esclient
        .prepareSearch(configurationService.getOptimizeIndex())
        .setTypes(configurationService.getEventType());

    BoolQueryBuilder query = setupBaseQuery(processDefinitionId);

    srb = srb.setQuery(query);
    Terms activities = getTermsWithAggregation(srb);
    for (Terms.Bucket b : activities.getBuckets()) {
      result.put(b.getKeyAsString(), b.getDocCount());
    }
    return result;
  }

  private BoolQueryBuilder setupBaseQuery(String processDefinitionId) {
    BoolQueryBuilder query;
    query = QueryBuilders.boolQuery()
        .must(QueryBuilders.matchQuery("processDefinitionId", processDefinitionId));
    return query;
  }

  public Map<String, Long> getHeatMap(HeatMapQueryDto dto) {
    ValidationHelper.validate(dto);
    Map<String, Long> result = new HashMap<>();

    SearchRequestBuilder srb = esclient
        .prepareSearch(configurationService.getOptimizeIndex())
        .setTypes(configurationService.getEventType());

    BoolQueryBuilder query = setupBaseQuery(dto.getProcessDefinitionId());

    query = dateFilterHelper.addFilters(query, dto.getFilter());

    srb = srb.setQuery(query);
    Terms activities = getTermsWithAggregation(srb);
    for (Terms.Bucket b : activities.getBuckets()) {
      result.put(b.getKeyAsString(), b.getDocCount());
    }
    return result;
  }



  private Terms getTermsWithAggregation(SearchRequestBuilder srb) {
    SearchResponse sr = srb
        .addAggregation(AggregationBuilders
            .terms("activities")
            .field("activityId")
        )
        .execute().actionGet();

    return sr.getAggregations().get("activities");
  }

}

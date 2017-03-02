package org.camunda.optimize.service.es.reader;

import org.camunda.optimize.dto.optimize.HeatMapQueryDto;
import org.camunda.optimize.dto.optimize.HeatMapResponseDto;
import org.camunda.optimize.service.es.mapping.DateFilterHelper;
import org.camunda.optimize.service.util.ConfigurationService;
import org.camunda.optimize.service.util.ValidationHelper;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.cardinality.Cardinality;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Askar Akhmerov
 */
@Component
public class HeatMapReader {
  public static final String PI_COUNT = "piCount";
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

  public HeatMapResponseDto getHeatMap(String processDefinitionId) {
    HeatMapResponseDto result = new HeatMapResponseDto();
    Map<String, Long> flowNodes = new HashMap<>();
    result.setFlowNodes(flowNodes);

    SearchRequestBuilder srb = esclient
        .prepareSearch(configurationService.getOptimizeIndex())
        .setTypes(configurationService.getEventType());

    BoolQueryBuilder query = setupBaseQuery(processDefinitionId);

    srb = srb.setQuery(query);
    processAggregations(result, srb);
    return result;
  }

  private BoolQueryBuilder setupBaseQuery(String processDefinitionId) {
    BoolQueryBuilder query;
    query = QueryBuilders.boolQuery()
        .must(QueryBuilders.matchQuery("processDefinitionId", processDefinitionId));
    return query;
  }

  public HeatMapResponseDto getHeatMap(HeatMapQueryDto dto) {
    HeatMapResponseDto result = new HeatMapResponseDto();
    ValidationHelper.validate(dto);
    Map<String, Long> flowNodes = new HashMap<>();
    result.setFlowNodes(flowNodes);

    SearchRequestBuilder srb = esclient
        .prepareSearch(configurationService.getOptimizeIndex())
        .setTypes(configurationService.getEventType());

    BoolQueryBuilder query = setupBaseQuery(dto.getProcessDefinitionId());

    query = dateFilterHelper.addFilters(query, dto.getFilter());

    srb = srb.setQuery(query);
    processAggregations(result, srb);

    return result;
  }

  private void processAggregations(HeatMapResponseDto result, SearchRequestBuilder srb) {
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
        .execute().actionGet();

    Aggregations aggregations = sr.getAggregations();
    return aggregations;
  }

}

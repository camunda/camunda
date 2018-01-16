package org.camunda.optimize.service.es.reader;

import org.camunda.optimize.dto.optimize.query.heatmap.HeatMapQueryDto;
import org.camunda.optimize.dto.optimize.query.heatmap.HeatMapResponseDto;
import org.camunda.optimize.service.es.filter.QueryFilterEnhancer;
import org.camunda.optimize.service.util.ValidationHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public abstract class HeatMapReader {

  static final String MI_BODY = "multiInstanceBody";

  private final Logger logger = LoggerFactory.getLogger(HeatMapReader.class);

  @Autowired
  private Client esclient;
  @Autowired
  protected ConfigurationService configurationService;

  @Autowired
  private QueryFilterEnhancer queryFilterEnhancer;

  public HeatMapResponseDto getHeatMap(String processDefinitionId) {
    HeatMapQueryDto dto = new HeatMapQueryDto();
    dto.setProcessDefinitionId(processDefinitionId);
    dto.setFilter(Collections.emptyList());
    return getHeatMap(dto);
  }

  public HeatMapResponseDto getHeatMap(HeatMapQueryDto dto) {
    ValidationHelper.validate(dto);
    logger.debug("Fetching heat map for process definition: {}", dto.getProcessDefinitionId());

    HeatMapResponseDto result = new HeatMapResponseDto();
    Map<String, Long> flowNodes = new HashMap<>();
    result.setFlowNodes(flowNodes);

    SearchRequestBuilder srb = esclient
      .prepareSearch(configurationService.getOptimizeIndex(configurationService.getProcessInstanceType()))
      .setTypes(configurationService.getProcessInstanceType())
      .setFetchSource(false);

    BoolQueryBuilder query = setupBaseQuery(dto.getProcessDefinitionId());

    queryFilterEnhancer.addFilterToQuery(query, dto.getFilter());

    srb = srb.setQuery(query);
    processAggregations(result, srb);

    return result;
  }

  private BoolQueryBuilder setupBaseQuery(String processDefinitionId) {
    BoolQueryBuilder query;
    query = QueryBuilders.boolQuery()
      .must(QueryBuilders.termQuery("processDefinitionId", processDefinitionId));
    return query;
  }

  /**
   * Uses elasticsearch aggregations to fetch the heat map information
   * and adds them to the given result heat map response.
   *
   * @param result heat map response where the result of the heat map calculations should be stored.
   * @param srb    used to execute the aggregated search
   */
  abstract void processAggregations(HeatMapResponseDto result, SearchRequestBuilder srb);
}

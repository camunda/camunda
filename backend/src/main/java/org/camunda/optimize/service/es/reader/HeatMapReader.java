package org.camunda.optimize.service.es.reader;

import org.camunda.optimize.dto.optimize.FilterMapDto;
import org.camunda.optimize.dto.optimize.HeatMapQueryDto;
import org.camunda.optimize.dto.optimize.HeatMapResponseDto;
import org.camunda.optimize.service.es.mapping.DateFilterHelper;
import org.camunda.optimize.service.util.ConfigurationService;
import org.camunda.optimize.service.util.ValidationHelper;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;

public abstract class HeatMapReader {

  static final String MI_BODY = "multiInstanceBody";

  private final Logger logger = LoggerFactory.getLogger(HeatMapReader.class);

  @Autowired
  private TransportClient esclient;
  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private DateFilterHelper dateFilterHelper;

  public HeatMapResponseDto getHeatMap(String processDefinitionId) {
    HeatMapQueryDto dto = new HeatMapQueryDto();
    dto.setProcessDefinitionId(processDefinitionId);
    dto.setFilter(new FilterMapDto());
    return getHeatMap(dto);
  }

  private BoolQueryBuilder setupBaseQuery(String processDefinitionId) {
    BoolQueryBuilder query;
    query = QueryBuilders.boolQuery()
      .must(QueryBuilders.matchQuery("processDefinitionId", processDefinitionId))
      .mustNot(QueryBuilders.matchQuery("activityType", MI_BODY));
    return query;
  }

  public HeatMapResponseDto getHeatMap(HeatMapQueryDto dto) {
    ValidationHelper.validate(dto);
    logger.debug("Fetching heat map for process definition: {}", dto.getProcessDefinitionId());

    HeatMapResponseDto result = new HeatMapResponseDto();
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

  /**
   * Uses elasticsearch aggregations to fetch the heat map information
   * and adds them to the given result heat map response.
   *
   * @param result heat map response where the result of the heat map calculations should be stored.
   * @param srb    used to execute the aggregated search
   */
  abstract void processAggregations(HeatMapResponseDto result, SearchRequestBuilder srb);
}

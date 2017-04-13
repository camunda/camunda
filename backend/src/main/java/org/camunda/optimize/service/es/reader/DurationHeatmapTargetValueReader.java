package org.camunda.optimize.service.es.reader;

import org.camunda.optimize.dto.optimize.DurationHeatmapTargetValueDto;
import org.camunda.optimize.service.util.ConfigurationService;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.type.DurationHeatmapTargetValueType.ACTIVITY_ID;
import static org.camunda.optimize.service.es.schema.type.DurationHeatmapTargetValueType.PROCESS_DEFINITION_ID;
import static org.camunda.optimize.service.es.schema.type.DurationHeatmapTargetValueType.TARGET_VALUE;
import static org.camunda.optimize.service.es.schema.type.DurationHeatmapTargetValueType.TARGET_VALUE_LIST;

@Component
public class DurationHeatmapTargetValueReader {

  private final Logger logger = LoggerFactory.getLogger(DurationHeatmapTargetValueReader.class);

  @Autowired
  private TransportClient esclient;

  @Autowired
  private ConfigurationService configurationService;

  public DurationHeatmapTargetValueDto getTargetValues(String processDefinitionId) {
    logger.debug("Fetching duration heatmap target value for process definition: " + processDefinitionId);
    DurationHeatmapTargetValueDto dto = new DurationHeatmapTargetValueDto();
    dto.setProcessDefinitionId(processDefinitionId);
    dto.setTargetValues(Collections.emptyMap());
    try {
      GetResponse response = esclient
        .prepareGet(configurationService.getOptimizeIndex(), configurationService.getDurationHeatmapTargetValueType(), processDefinitionId)
        .get();

      Map<String, Object> responseMap = response.getSourceAsMap();
      dto.setProcessDefinitionId((String) responseMap.get(PROCESS_DEFINITION_ID));
      Map<String, String> targetValueMap = retrieveTargetValues(responseMap);
      dto.setTargetValues(targetValueMap);

    } catch (RuntimeException e) {
      logger.warn("Could not retrieve the duration heatmap target values for process definition " +
        processDefinitionId + " from elasticsearch!");
    }

    return dto;
  }

  private Map<String, String> retrieveTargetValues(Map<String, Object> responseMap) {
    Map<String, String> targetValueMap = new HashMap<>();

    List<Map<String, String>> targetValueList =
      (List<Map<String, String>>) responseMap.get(TARGET_VALUE_LIST);

    for (Map<String, String> stringStringMap : targetValueList) {
      String activityId = stringStringMap.get(ACTIVITY_ID);
      String targetValue = stringStringMap.get(TARGET_VALUE);
      targetValueMap.put(activityId, targetValue);
    }
    return targetValueMap;
  }
}

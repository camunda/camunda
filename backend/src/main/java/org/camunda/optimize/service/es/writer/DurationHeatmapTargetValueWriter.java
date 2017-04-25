package org.camunda.optimize.service.es.writer;

import org.camunda.optimize.dto.optimize.DurationHeatmapTargetValueDto;
import org.camunda.optimize.service.es.schema.type.DurationHeatmapTargetValueType;
import org.camunda.optimize.service.util.ConfigurationService;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.transport.TransportClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DurationHeatmapTargetValueWriter {

  @Autowired
  private TransportClient esclient;

  @Autowired
  private ConfigurationService configurationService;

  private final Logger logger = LoggerFactory.getLogger(DurationHeatmapTargetValueWriter.class);

  public void persistTargetValue(DurationHeatmapTargetValueDto dto) {
    Map<String, Object> source = new HashMap<>();
    source.put(DurationHeatmapTargetValueType.PROCESS_DEFINITION_ID, dto.getProcessDefinitionId());
    source.put(DurationHeatmapTargetValueType.TARGET_VALUE_LIST, createActivityTargetValueMap(dto));

    logger.debug("Writing target value of process definition {} to elasticsearch", dto.getProcessDefinitionId());
    esclient
      .prepareIndex(configurationService.getOptimizeIndex(), configurationService.getDurationHeatmapTargetValueType(), dto.getProcessDefinitionId())
      .setSource(source)
      .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
      .get();
  }

  private List<Map<String, String>> createActivityTargetValueMap(DurationHeatmapTargetValueDto dto) {
    List<Map<String, String>> activityTargetValueList = new ArrayList<>(dto.getTargetValues().size());
    for (String activityId : dto.getTargetValues().keySet()) {
      Map<String, String> activityTargetValueMap = new HashMap<>(2);
      activityTargetValueMap.put(DurationHeatmapTargetValueType.ACTIVITY_ID, activityId);
      activityTargetValueMap.put(DurationHeatmapTargetValueType.TARGET_VALUE, dto.getTargetValues().get(activityId));
      activityTargetValueList.add(activityTargetValueMap);
    }
    return activityTargetValueList;
  }
}

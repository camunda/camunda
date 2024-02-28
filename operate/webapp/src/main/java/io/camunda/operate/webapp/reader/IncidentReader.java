/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.reader;

import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.webapp.data.IncidentDataHolder;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentResponseDto;
import java.util.List;
import java.util.Map;

public interface IncidentReader {
  List<IncidentEntity> getAllIncidentsByProcessInstanceKey(Long processInstanceKey);

  Map<Long, List<Long>> getIncidentKeysPerProcessInstance(List<Long> processInstanceKeys);

  IncidentEntity getIncidentById(Long incidentKey);

  IncidentResponseDto getIncidentsByProcessInstanceId(String processInstanceId);

  Map<String, IncidentDataHolder> collectFlowNodeDataForPropagatedIncidents(
      List<IncidentEntity> incidents, String processInstanceId, String currentTreePath);
}

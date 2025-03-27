/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.reader;

import io.camunda.operate.webapp.data.IncidentDataHolder;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentResponseDto;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
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

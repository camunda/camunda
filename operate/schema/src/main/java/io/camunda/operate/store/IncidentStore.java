/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store;

import io.camunda.webapps.schema.entities.incident.ErrorType;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import java.util.List;
import java.util.Map;

public interface IncidentStore {

  IncidentEntity getIncidentById(Long incidentKey);

  List<IncidentEntity> getIncidentsWithErrorTypesFor(
      String treePath, List<Map<ErrorType, Long>> errorTypes);

  List<IncidentEntity> getIncidentsByProcessInstanceKey(Long processInstanceKey);

  List<IncidentEntity> getIncidentsByErrorHashCode(final Integer incidentErrorHashCode);

  /**
   * Returns map of incident ids per process instance id.
   *
   * @param processInstanceKeys
   * @return
   */
  Map<Long, List<Long>> getIncidentKeysPerProcessInstance(List<Long> processInstanceKeys);
}

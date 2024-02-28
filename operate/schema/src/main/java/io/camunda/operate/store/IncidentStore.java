/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store;

import io.camunda.operate.entities.ErrorType;
import io.camunda.operate.entities.IncidentEntity;
import java.util.List;
import java.util.Map;

public interface IncidentStore {

  IncidentEntity getIncidentById(Long incidentKey);

  List<IncidentEntity> getIncidentsWithErrorTypesFor(
      String treePath, List<Map<ErrorType, Long>> errorTypes);

  List<IncidentEntity> getIncidentsByProcessInstanceKey(Long processInstanceKey);

  /**
   * Returns map of incident ids per process instance id.
   *
   * @param processInstanceKeys
   * @return
   */
  Map<Long, List<Long>> getIncidentKeysPerProcessInstance(List<Long> processInstanceKeys);
}

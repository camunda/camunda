/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store;

import java.util.Map;
import java.util.Set;

public interface FlowNodeStore {

  String getFlowNodeIdByFlowNodeInstanceId(String flowNodeInstanceId);

  Map<String, String> getFlowNodeIdsForFlowNodeInstances(Set<String> flowNodeInstances);

  String findParentTreePathFor(final long parentFlowNodeInstanceKey);
}

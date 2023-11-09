/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer.activity;

import java.util.Set;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_CANCELED;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_DEFINITION_KEY;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_DEFINITION_VERSION;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_ID;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCE_ID;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_TENANT_ID;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.USER_TASK_INSTANCE_ID;

public interface CompletedActivityInstanceWriter extends AbstractActivityInstanceWriter {

  Set<String> USER_TASK_FIELDS_TO_UPDATE = Set.of(
    FLOW_NODE_ID, USER_TASK_INSTANCE_ID, FLOW_NODE_INSTANCE_ID, FLOW_NODE_CANCELED,
    FLOW_NODE_DEFINITION_KEY, FLOW_NODE_DEFINITION_VERSION, FLOW_NODE_TENANT_ID
  );

  String UPDATE_USER_TASK_FIELDS_SCRIPT = USER_TASK_FIELDS_TO_UPDATE
    .stream()
    .map(fieldKey -> String.format("existingTask.%s = newFlowNode.%s;%n", fieldKey, fieldKey))
    .collect(Collectors.joining());

}

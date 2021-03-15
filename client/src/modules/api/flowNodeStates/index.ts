/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {get} from 'modules/request';

async function fetchFlowNodeStates(
  workflowInstanceId: WorkflowInstanceEntity['id']
) {
  return get(`/api/workflow-instances/${workflowInstanceId}/flow-node-states`);
}

export {fetchFlowNodeStates};

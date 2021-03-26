/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {post} from 'modules/request';

async function fetchFlowNodeMetaData({
  processInstanceId,
  flowNodeId,
  flowNodeInstanceId,
  flowNodeType,
}: {
  processInstanceId: ProcessInstanceEntity['id'];
  flowNodeId: string;
  flowNodeInstanceId?: string;
  flowNodeType?: string;
}) {
  const URL = `/api/process-instances/${processInstanceId}/flow-node-metadata`;

  if (flowNodeInstanceId === undefined) {
    return post(URL, {flowNodeId, flowNodeInstanceId, flowNodeType});
  } else {
    return post(URL, {flowNodeInstanceId, flowNodeType});
  }
}

export {fetchFlowNodeMetaData};

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {request} from 'modules/request';

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
  return request({
    url: `/api/process-instances/${processInstanceId}/flow-node-metadata`,
    method: 'POST',
    body:
      flowNodeInstanceId === undefined
        ? {flowNodeId, flowNodeInstanceId, flowNodeType}
        : {flowNodeInstanceId, flowNodeType},
  });
}

export {fetchFlowNodeMetaData};

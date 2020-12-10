/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {post} from 'modules/request';

const URL = '/api/flow-node-instances';

async function fetchFlowNodeInstances({
  workflowInstanceId,
  pageSize,
  parentTreePath,
}: {
  workflowInstanceId: WorkflowInstanceEntity['id'];
  pageSize: number;
  parentTreePath: string;
}) {
  return post(URL, {
    workflowInstanceId,
    pageSize,
    parentTreePath,
  });
}

export {fetchFlowNodeInstances};

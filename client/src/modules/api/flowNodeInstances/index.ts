/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {post} from 'modules/request';
import {FlowNodeInstance} from 'modules/stores/flowNodeInstance';

const URL = '/api/flow-node-instances';

async function fetchFlowNodeInstances({
  workflowInstanceId,
  pageSize,
  parentTreePath,
  searchAfter,
  searchBefore,
}: {
  workflowInstanceId: WorkflowInstanceEntity['id'];
  pageSize: number;
  parentTreePath: string;
  searchAfter?: FlowNodeInstance['sortValues'];
  searchBefore?: FlowNodeInstance['sortValues'];
}) {
  return post(URL, {
    workflowInstanceId,
    pageSize,
    parentTreePath,
    searchAfter,
    searchBefore,
  });
}

export {fetchFlowNodeInstances};

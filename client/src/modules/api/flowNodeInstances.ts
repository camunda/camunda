/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {request} from 'modules/request';
import {FlowNodeInstance} from 'modules/stores/flowNodeInstance';

type Query = {
  processInstanceId: ProcessInstanceEntity['id'];
  treePath: FlowNodeInstance['treePath'];
  pageSize?: number;
  searchAfter?: FlowNodeInstance['sortValues'];
  searchBefore?: FlowNodeInstance['sortValues'];
  searchAfterOrEqual?: FlowNodeInstance['sortValues'];
};

async function fetchFlowNodeInstances(queries: Query[]) {
  return request({
    url: '/api/flow-node-instances',
    method: 'POST',
    body: {queries},
  });
}

export {fetchFlowNodeInstances};

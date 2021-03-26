/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {post} from 'modules/request';
import {FlowNodeInstance} from 'modules/stores/flowNodeInstance';

const URL = '/api/flow-node-instances';

type Query = {
  processInstanceId: ProcessInstanceEntity['id'];
  treePath: FlowNodeInstance['treePath'];
  pageSize?: number;
  searchAfter?: FlowNodeInstance['sortValues'];
  searchBefore?: FlowNodeInstance['sortValues'];
  searchAfterOrEqual?: FlowNodeInstance['sortValues'];
};

async function fetchFlowNodeInstances(queries: Query[]) {
  return post(URL, {queries});
}

export {fetchFlowNodeInstances};

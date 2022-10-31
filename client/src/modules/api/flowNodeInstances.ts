/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {requestAndParse} from 'modules/request';
import {FlowNodeInstance} from 'modules/stores/flowNodeInstance';

type Query = {
  processInstanceId: ProcessInstanceEntity['id'];
  treePath: FlowNodeInstance['treePath'];
  pageSize?: number;
  searchAfter?: FlowNodeInstance['sortValues'];
  searchBefore?: FlowNodeInstance['sortValues'];
  searchAfterOrEqual?: FlowNodeInstance['sortValues'];
};

type FlowNodeInstanceDto = {
  id: string;
  type: string;
  state?: InstanceEntityState;
  flowNodeId: string;
  startDate: string;
  endDate: null | string;
  treePath: string;
  sortValues: [string, string] | [];
};

type FlowNodeInstancesDto<T> = {
  [treePath: string]: {
    running: boolean | null;
    children: T[];
  };
};

async function fetchFlowNodeInstances(queries: Query[]) {
  return requestAndParse<FlowNodeInstancesDto<FlowNodeInstanceDto>>({
    url: '/api/flow-node-instances',
    method: 'POST',
    body: {queries},
  });
}

export {fetchFlowNodeInstances};
export type {FlowNodeInstanceDto, FlowNodeInstancesDto};

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {requestAndParse} from 'modules/request';
import {type FlowNodeInstance} from 'modules/stores/flowNodeInstance';
import type {
  ProcessInstanceEntity,
  InstanceEntityState,
} from 'modules/types/operate';

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

const fetchFlowNodeInstances = async (
  queries: Query[],
  options?: Parameters<typeof requestAndParse>[1],
) => {
  return requestAndParse<FlowNodeInstancesDto<FlowNodeInstanceDto>>(
    {
      url: '/api/flow-node-instances',
      method: 'POST',
      body: {queries},
    },
    options,
  );
};

export {fetchFlowNodeInstances};
export type {FlowNodeInstanceDto, FlowNodeInstancesDto};

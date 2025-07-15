/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {requestAndParse} from 'modules/request';
import type {
  ProcessInstanceEntity,
  OperationEntity,
} from 'modules/types/operate';

type FlowNodeVariables = {
  [flowNodeId: string]: Array<{[variableName: string]: string}>;
};
type ModificationPayload = {
  modifications: Array<
    | {
        modification: 'ADD_TOKEN' | 'CANCEL_TOKEN' | 'MOVE_TOKEN';
        toFlowNodeId?: string;
        fromFlowNodeId?: string;
        newTokensCount?: number;
        variables?: FlowNodeVariables;
        fromFlowNodeInstanceKey?: string;
        ancestorElementInstanceKey?: string;
      }
    | {
        modification: 'ADD_VARIABLE' | 'EDIT_VARIABLE';
        scopeKey: string;
        variables: {[variableName: string]: string};
      }
  >;
};

async function modify({
  processInstanceId,
  payload,
}: {
  processInstanceId: ProcessInstanceEntity['id'];
  payload: ModificationPayload;
}) {
  return requestAndParse<OperationEntity>({
    url: `/api/process-instances/${processInstanceId}/modify`,
    method: 'POST',
    body: payload,
  });
}

export type {ModificationPayload, FlowNodeVariables};
export {modify};

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {requestAndParse} from 'modules/request';

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

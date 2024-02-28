/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {requestAndParse} from 'modules/request';

type VariablePayload = {
  pageSize: number;
  scopeId: string;
  searchAfter?: ReadonlyArray<string>;
  searchAfterOrEqual?: ReadonlyArray<string>;
  searchBefore?: ReadonlyArray<string>;
  searchBeforeOrEqual?: ReadonlyArray<string>;
};

const fetchVariables = async ({
  instanceId,
  payload,
  signal,
}: {
  instanceId: ProcessInstanceEntity['id'];
  payload: VariablePayload;
  signal?: AbortSignal;
}) => {
  return requestAndParse<VariableEntity[]>({
    url: `/api/process-instances/${instanceId}/variables`,
    method: 'POST',
    body: payload,
    signal,
  });
};

export {fetchVariables};
export type {VariablePayload};

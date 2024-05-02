/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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

const fetchVariables = async (
  {
    instanceId,
    payload,
    signal,
  }: {
    instanceId: ProcessInstanceEntity['id'];
    payload: VariablePayload;
    signal?: AbortSignal;
  },
  options?: Parameters<typeof requestAndParse>[1],
) => {
  return requestAndParse<VariableEntity[]>(
    {
      url: `/api/process-instances/${instanceId}/variables`,
      method: 'POST',
      body: payload,
      signal,
    },
    options,
  );
};

export {fetchVariables};
export type {VariablePayload};

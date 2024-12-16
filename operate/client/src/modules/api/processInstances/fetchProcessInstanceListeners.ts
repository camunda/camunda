/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {requestAndParse} from 'modules/request';

type ListenersDto = {
  listeners: ListenerEntity[];
  totalCount: number;
};

type ListenerPayload = {
  flowNodeId?: string;
  flowNodeInstanceId?: string;
  listenerTypeFilter?: ListenerEntity['listenerType'];
  sorting?: {
    sortBy: string;
    sortOrder: 'desc' | 'asc';
  };
  searchAfter?: ReadonlyArray<string>;
  searchBefore?: ReadonlyArray<string>;
  pageSize?: number;
};

type fetchProcessInstanceListenersParams = {
  processInstanceId: ProcessInstanceEntity['id'];
  payload: ListenerPayload;
  options?: Parameters<typeof requestAndParse>[1];
};

const fetchProcessInstanceListeners = async ({
  processInstanceId,
  payload,
  options,
}: fetchProcessInstanceListenersParams) => {
  return await requestAndParse<ListenersDto>(
    {
      url: `/api/process-instances/${processInstanceId}/listeners`,
      method: 'POST',
      body: payload,
    },
    options,
  );
};

export {fetchProcessInstanceListeners};
export type {ListenersDto, ListenerPayload};

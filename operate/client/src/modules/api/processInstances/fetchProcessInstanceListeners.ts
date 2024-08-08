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
  flowNodeId: string;
  pageSize?: number;
};

type fetchProcessInstanceListenersParams = {
  processInstanceId: ProcessInstanceEntity['id'];
  payload: ListenerPayload;
};

const fetchProcessInstanceListeners = async (
  {processInstanceId, payload}: fetchProcessInstanceListenersParams,
  options?: Parameters<typeof requestAndParse>[1],
) => {
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

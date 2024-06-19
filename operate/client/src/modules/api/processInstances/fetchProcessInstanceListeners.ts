/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {requestAndParse} from 'modules/request';

type ListenerPayload = {
  flowNodeId: string;
  pageSize?: number;
};

const fetchProcessInstanceListeners = async ({
  processInstanceId,
  payload,
  options,
}: {
  processInstanceId: ProcessInstanceEntity['id'];
  payload: ListenerPayload;
  options?: Parameters<typeof requestAndParse>[1];
}) => {
  return await requestAndParse<ListenerEntity[]>(
    {
      url: `/api/process-instances/${processInstanceId}/listeners`,
      method: 'POST',
      body: payload,
    },
    options,
  );
};

export {fetchProcessInstanceListeners};

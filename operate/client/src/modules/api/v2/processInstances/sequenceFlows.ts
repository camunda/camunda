/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  endpoints,
  type GetProcessInstanceSequenceFlowsResponseBody,
} from '@vzeta/camunda-api-zod-schemas/8.8';
import {requestWithThrow} from 'modules/request';

const fetchProcessSequenceFlows = async (processInstanceKey: string) => {
  return requestWithThrow<GetProcessInstanceSequenceFlowsResponseBody>({
    url: endpoints.getProcessInstanceSequenceFlows.getUrl({
      processInstanceKey: processInstanceKey,
    }),
    method: endpoints.getProcessInstanceSequenceFlows.method,
  });
};

export {fetchProcessSequenceFlows};

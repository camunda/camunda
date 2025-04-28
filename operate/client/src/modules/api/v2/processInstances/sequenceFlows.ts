/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  endpoints,
  GetProcessSequenceFlowsResponseBody,
} from '@vzeta/camunda-api-zod-schemas/operate';
import {RequestResult, requestWithThrow} from 'modules/request';

const fetchProcessSequenceFlows = async (
  processInstanceKey: string,
): RequestResult<GetProcessSequenceFlowsResponseBody> => {
  return requestWithThrow<GetProcessSequenceFlowsResponseBody>({
    url: endpoints.getProcessSequenceFlows.getUrl({
      processInstanceKey: processInstanceKey,
    }),
    method: endpoints.getProcessSequenceFlows.method,
  });
};

export {fetchProcessSequenceFlows};

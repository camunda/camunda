/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  endpoints,
  type ProcessInstance,
} from '@vzeta/camunda-api-zod-schemas/8.8';
import {requestWithThrow} from 'modules/request';

const fetchProcessInstance = async (processInstanceKey: string) => {
  return requestWithThrow<ProcessInstance>({
    url: endpoints.getProcessInstance.getUrl({
      processInstanceKey,
    }),
    method: endpoints.getProcessInstance.method,
  });
};

export {fetchProcessInstance};

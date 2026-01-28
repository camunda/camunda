/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import {
  endpoints,
  type DeleteProcessInstanceRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.9';
import {request} from 'modules/request';

const deleteProcessInstance = async (
  processInstanceKey: ProcessInstance['processInstanceKey'],
  payload?: DeleteProcessInstanceRequestBody,
) => {
  return request({
    url: endpoints.deleteProcessInstance.getUrl({processInstanceKey}),
    method: endpoints.deleteProcessInstance.method,
    body: payload,
    responseType: 'none',
  });
};

export {deleteProcessInstance};

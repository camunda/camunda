/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  endpoints,
  type DeleteResourceRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.9';
import {request} from 'modules/request';

const deleteResource = async (
  resourceKey: string,
  payload?: DeleteResourceRequestBody,
) => {
  return request({
    url: endpoints.deleteResource.getUrl({resourceKey}),
    method: endpoints.deleteResource.method,
    body: payload,
    responseType: 'json',
  });
};

export {deleteResource};

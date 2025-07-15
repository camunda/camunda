/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  endpoints,
  type GetElementInstanceResponseBody,
} from '@vzeta/camunda-api-zod-schemas/8.8';
import {requestWithThrow, type RequestResult} from 'modules/request';

const fetchElementInstance = async (params: {
  elementInstanceKey: string;
}): RequestResult<GetElementInstanceResponseBody> => {
  return requestWithThrow<GetElementInstanceResponseBody>({
    url: (
      endpoints.getElementInstance.getUrl as (params: {
        elementInstanceKey: string;
      }) => string
    )(params),
    method: endpoints.getElementInstance.method,
  });
};

export {fetchElementInstance};

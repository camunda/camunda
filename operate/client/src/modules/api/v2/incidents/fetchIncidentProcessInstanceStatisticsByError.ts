/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  endpoints,
  type GetIncidentProcessInstanceStatisticsByErrorRequestBody,
  type GetIncidentProcessInstanceStatisticsByErrorResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.9';
import {requestWithThrow} from 'modules/request';

const fetchIncidentProcessInstanceStatisticsByError = async (
  payload: GetIncidentProcessInstanceStatisticsByErrorRequestBody,
) => {
  return requestWithThrow<GetIncidentProcessInstanceStatisticsByErrorResponseBody>(
    {
      url: endpoints.getIncidentProcessInstanceStatisticsByError.getUrl(),
      method: endpoints.getIncidentProcessInstanceStatisticsByError.method,
      body: payload,
    },
  );
};

export {fetchIncidentProcessInstanceStatisticsByError};

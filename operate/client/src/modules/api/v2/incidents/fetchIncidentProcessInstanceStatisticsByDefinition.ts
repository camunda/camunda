/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  endpoints,
  type GetIncidentProcessInstanceStatisticsByDefinitionRequestBody,
  type GetIncidentProcessInstanceStatisticsByDefinitionResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.9';
import {requestWithThrow} from 'modules/request';

const fetchIncidentProcessInstanceStatisticsByDefinition = async (
  payload: GetIncidentProcessInstanceStatisticsByDefinitionRequestBody,
) => {
  return requestWithThrow<GetIncidentProcessInstanceStatisticsByDefinitionResponseBody>(
    {
      url: endpoints.getIncidentProcessInstanceStatisticsByDefinition.getUrl(),
      method: endpoints.getIncidentProcessInstanceStatisticsByDefinition.method,
      body: payload,
    },
  );
};

export {fetchIncidentProcessInstanceStatisticsByDefinition};

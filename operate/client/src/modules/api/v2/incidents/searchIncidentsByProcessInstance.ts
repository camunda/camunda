/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  endpoints,
  type QueryIncidentsRequestBody,
  type QueryIncidentsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {requestWithThrow} from 'modules/request';

const searchIncidentsByProcessInstance = async (
  processInstanceKey: string,
  payload?: QueryIncidentsRequestBody,
) => {
  return requestWithThrow<QueryIncidentsResponseBody>({
    url: endpoints.queryProcessInstanceIncidents.getUrl({processInstanceKey}),
    method: endpoints.queryProcessInstanceIncidents.method,
    body: payload,
  });
};

export {searchIncidentsByProcessInstance};

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  endpoints,
  type QueryElementInstanceIncidentsRequestBody,
  type QueryElementInstanceIncidentsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {requestWithThrow} from 'modules/request';

const searchIncidentsByElementInstance = async (
  elementInstanceKey: string,
  payload?: QueryElementInstanceIncidentsRequestBody,
) => {
  return requestWithThrow<QueryElementInstanceIncidentsResponseBody>({
    url: endpoints.queryElementInstanceIncidents.getUrl({elementInstanceKey}),
    method: endpoints.queryElementInstanceIncidents.method,
    body: payload,
  });
};

export {searchIncidentsByElementInstance};

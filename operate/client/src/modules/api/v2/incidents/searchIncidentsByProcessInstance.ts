/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  endpoints,
  type QueryIncidentsResponseBody,
} from '@vzeta/camunda-api-zod-schemas/8.8';
import {type RequestResult, requestWithThrow} from 'modules/request';

const searchIncidentsByProcessInstance = async (payload: {
  processInstanceKey: string;
}): RequestResult<QueryIncidentsResponseBody> => {
  return requestWithThrow<QueryIncidentsResponseBody>({
    url: endpoints.queryProcessInstanceIncidents.getUrl(payload),
    method: endpoints.queryProcessInstanceIncidents.method,
  });
};

export {searchIncidentsByProcessInstance};

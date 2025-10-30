/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {endpoints} from '@camunda/camunda-api-zod-schemas/8.8';
import {request} from 'modules/request';

const resolveIncident = (incidentKey: string) => {
  return request({
    url: endpoints.resolveIncident.getUrl({incidentKey}),
    method: endpoints.resolveIncident.method,
  });
};

export {resolveIncident};

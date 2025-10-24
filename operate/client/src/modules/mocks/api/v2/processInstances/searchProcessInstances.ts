/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {mockPostRequest} from '../../mockRequest';
import {
  endpoints,
  type QueryProcessInstancesResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.8';

const mockSearchProcessInstances = () =>
  mockPostRequest<QueryProcessInstancesResponseBody>(
    endpoints.queryProcessInstances.getUrl(),
  );

export {mockSearchProcessInstances};

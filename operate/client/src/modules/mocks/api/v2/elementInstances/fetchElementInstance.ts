/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {mockGetRequest} from '../../mockRequest';
import type {GetElementInstanceResponseBody} from '@vzeta/camunda-api-zod-schemas';
import {endpoints} from '@vzeta/camunda-api-zod-schemas';

const mockFetchElementInstance = (
  elementInstanceKey: string,
  contextPath = '',
) =>
  mockGetRequest<GetElementInstanceResponseBody>(
    `${contextPath}${endpoints.getElementInstance.getUrl({
      elementInstanceKey,
    })}`,
  );

export {mockFetchElementInstance};

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {isBusinessValueMockEnabled as isBusinessValueMockEnabledConfig} from 'config';
import {get} from 'request';
import type {BusinessValueSummary, BusinessValueFilter} from './types';
import {loadSummary as loadMockSummary} from './service.mock';

export async function loadSummary(filter?: BusinessValueFilter): Promise<BusinessValueSummary> {
  if (await isBusinessValueMockEnabledConfig()) {
    return loadMockSummary(filter);
  }

  if (!filter) {
    const response = await get('api/business-value/summary');
    return response.json();
  }

  const response = await get('api/business-value/summary', {
    startDate: filter.startDate,
    endDate: filter.endDate,
    ...(filter.tenantId ? {tenantId: filter.tenantId} : {}),
  });
  return response.json();
}

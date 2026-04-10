/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {BUSINESS_VALUE_FIXTURE} from './fixtures';
import type {BusinessValueSummary, BusinessValueFilter} from './types';

export async function loadSummary(_filter?: BusinessValueFilter): Promise<BusinessValueSummary> {
  return Promise.resolve(BUSINESS_VALUE_FIXTURE);
}

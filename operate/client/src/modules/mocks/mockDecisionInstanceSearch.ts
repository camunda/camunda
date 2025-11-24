/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QueryDecisionInstancesResponseBody} from '@camunda/camunda-api-zod-schemas/8.8';
import {
  assignApproverGroup,
  invoiceClassification,
} from './mockDecisionInstance';

const mockDecisionInstancesSearchResult: QueryDecisionInstancesResponseBody = {
  page: {totalItems: 2},
  items: [invoiceClassification, assignApproverGroup],
};

const mockEmptyDecisionInstancesSearchResult: QueryDecisionInstancesResponseBody =
  {
    page: {totalItems: 0},
    items: [],
  };

export {
  mockDecisionInstancesSearchResult,
  mockEmptyDecisionInstancesSearchResult,
};

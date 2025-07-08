/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {requestAndParse} from 'modules/request';
import {type DecisionRequestFilters} from 'modules/utils/filter';
import type {DecisionInstanceEntity} from 'modules/types/operate';

type DecisionInstancesQuery = {
  query: DecisionRequestFilters;
  sorting?: {
    sortBy: string;
    sortOrder: 'desc' | 'asc';
  };
  searchAfter?: ReadonlyArray<string>;
  searchBefore?: ReadonlyArray<string>;
  pageSize?: number;
};

type DecisionInstancesDto = {
  decisionInstances: DecisionInstanceEntity[];
  totalCount: number;
};

const fetchDecisionInstances = async (payload: DecisionInstancesQuery) => {
  return requestAndParse<DecisionInstancesDto>({
    url: '/api/decision-instances',
    method: 'POST',
    body: payload,
  });
};

export {fetchDecisionInstances};
export type {DecisionInstancesDto};

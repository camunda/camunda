/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {request} from 'modules/request';
import {DecisionRequestFilters} from 'modules/utils/filter';

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

async function fetchDecisionXML(decisionDefinitionId: string) {
  return request({
    url: `/api/decisions/${decisionDefinitionId}/xml`,
    method: 'GET',
  });
}

async function fetchDecisionInstance(decisionInstanceId: string) {
  return request({
    url: `/api/decision-instances/${decisionInstanceId}`,
    method: 'GET',
  });
}

async function fetchDecisionInstances(payload: DecisionInstancesQuery) {
  return request({
    url: '/api/decision-instances',
    method: 'POST',
    body: payload,
  });
}

async function fetchDrdData(decisionInstanceId: string) {
  return request({
    url: `/api/decision-instances/${decisionInstanceId}/drd-data`,
  });
}

async function fetchGroupedDecisions() {
  return request({
    url: '/api/decisions/grouped',
  });
}

export {
  fetchDecisionXML,
  fetchDecisionInstance,
  fetchDecisionInstances,
  fetchDrdData,
  fetchGroupedDecisions,
};

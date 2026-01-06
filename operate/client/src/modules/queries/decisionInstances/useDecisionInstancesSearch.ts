/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {searchDecisionInstances} from 'modules/api/v2/decisionInstances/searchDecisionInstances';
import type {QueryDecisionInstancesRequestBody} from '@camunda/camunda-api-zod-schemas/8.8';

const DECISION_INSTANCES_SEARCH_QUERY_KEY = 'decisionInstancesSearch';

const useDecisionInstancesSearch = (
  payload: QueryDecisionInstancesRequestBody,
  {enabled} = {enabled: true},
) => {
  return useQuery({
    queryKey: [DECISION_INSTANCES_SEARCH_QUERY_KEY, payload],
    queryFn: () =>
      searchDecisionInstances(payload).then(({response, error}) => {
        if (response !== null) {
          return response;
        }
        throw error;
      }),
    enabled,
  });
};

export {useDecisionInstancesSearch};

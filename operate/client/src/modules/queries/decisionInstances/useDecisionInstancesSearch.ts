/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {searchDecisionInstances} from 'modules/api/v2/decisionInstances/searchDecisionInstances';
import {QueryDecisionInstancesRequestBody} from '@vzeta/camunda-api-zod-schemas';

const DECISION_INSTANCES_SEARCH_QUERY_KEY = 'decisionInstancesSearch';

function getQueryKey(params: QueryDecisionInstancesRequestBody) {
  return [DECISION_INSTANCES_SEARCH_QUERY_KEY, params];
}

const useElementInstancesSearch = (
  params: QueryDecisionInstancesRequestBody,
) => {
  return useQuery({
    queryKey: getQueryKey(params),
    queryFn: () =>
      searchDecisionInstances(params).then(({response, error}) => {
        if (response !== null) return response;
        throw error;
      }),
    enabled: !!params,
  });
};

export {useElementInstancesSearch, DECISION_INSTANCES_SEARCH_QUERY_KEY};

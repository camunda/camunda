/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {searchElementInstances} from 'modules/api/v2/elementInstances/searchElementInstances';
import type {QueryElementInstancesRequestBody} from '@vzeta/camunda-api-zod-schemas/8.8';

const ELEMENT_INSTANCES_SEARCH_QUERY_KEY = 'elementInstancesSearch';

function getQueryKey(params: QueryElementInstancesRequestBody) {
  return [ELEMENT_INSTANCES_SEARCH_QUERY_KEY, params];
}

const useElementInstancesSearch = (
  params: QueryElementInstancesRequestBody,
) => {
  return useQuery({
    queryKey: getQueryKey(params),
    queryFn: () =>
      searchElementInstances(params).then(({response, error}) => {
        if (response !== null) return response;
        throw error;
      }),
    enabled: !!params,
  });
};

export {useElementInstancesSearch, ELEMENT_INSTANCES_SEARCH_QUERY_KEY};

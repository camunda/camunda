/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {searchElementInstances} from 'modules/api/v2/elementInstances/searchElementInstances';
import {QueryElementInstancesRequestBody} from '@vzeta/camunda-api-zod-schemas';

const ELEMENT_INSTANCES_SEARCH_QUERY_KEY = 'elementInstancesSearch';

const useElementInstancesSearch = (
  payload: QueryElementInstancesRequestBody,
  {enabled} = {enabled: true},
) => {
  return useQuery({
    queryKey: [ELEMENT_INSTANCES_SEARCH_QUERY_KEY, payload],
    queryFn: () =>
      searchElementInstances(payload).then(({response, error}) => {
        if (response !== null) return response;
        throw error;
      }),
    enabled,
  });
};

export {useElementInstancesSearch};

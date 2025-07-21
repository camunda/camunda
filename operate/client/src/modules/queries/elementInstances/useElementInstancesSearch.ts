/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {searchElementInstances} from 'modules/api/v2/elementInstances/searchElementInstances';
import type {
  QueryElementInstancesRequestBody,
  ElementInstance,
} from '@vzeta/camunda-api-zod-schemas/8.8';

const ELEMENT_INSTANCES_SEARCH_QUERY_KEY = 'elementInstancesSearch';

const useElementInstancesSearch = (
  elementId: string,
  processInstanceKey: string,
  elementType: ElementInstance['type'],
  options: {enabled: boolean} = {enabled: true},
) => {
  return useQuery({
    queryKey: [
      ELEMENT_INSTANCES_SEARCH_QUERY_KEY,
      elementId,
      processInstanceKey,
      elementType,
    ],
    queryFn: async () => {
      const payload: QueryElementInstancesRequestBody = {
        filter: {
          elementId,
          processInstanceKey,
          type: elementType ?? undefined,
        },
        page: {limit: 1},
      };
      const {response, error} = await searchElementInstances(payload);
      if (response !== null) {
        return response;
      }
      throw error;
    },
    ...options,
  });
};

export {useElementInstancesSearch};

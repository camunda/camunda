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
} from '@camunda/camunda-api-zod-schemas/8.8';
import {queryKeys} from '../queryKeys';

type UseElementInstancesSearchParams = {
  elementId: string;
  processInstanceKey: string;
  elementType?: ElementInstance['type'];
  enabled?: boolean;
};

const useElementInstancesSearch = (params: UseElementInstancesSearchParams) => {
  const {elementId, processInstanceKey, elementType, enabled = true} = params;

  return useQuery({
    queryKey: queryKeys.elementInstances.search({
      elementId,
      processInstanceKey,
      elementType,
    }),
    queryFn: async () => {
      const payload: QueryElementInstancesRequestBody = {
        filter: {
          elementId,
          processInstanceKey,
          type: elementType,
        },
        page: {limit: 1},
      };
      const {response, error} = await searchElementInstances(payload);
      if (response !== null) {
        return response;
      }
      throw error;
    },
    staleTime: 10000,
    enabled,
  });
};

export {useElementInstancesSearch};

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {searchElementInstances} from 'modules/api/v2/elementInstances/searchElementInstances';
import {queryKeys} from '../queryKeys';

const useSearchElementInstancesByScope = (
  payload: Parameters<typeof queryKeys.elementInstances.searchByScope>[0],
  options?: {
    enabled?: boolean;
  },
) => {
  return useQuery({
    queryKey: queryKeys.elementInstances.searchByScope(payload),
    queryFn: async () => {
      const {response, error} = await searchElementInstances(payload);
      if (response !== null) {
        return response;
      }
      throw error;
    },
    ...options,
  });
};

export {useSearchElementInstancesByScope};

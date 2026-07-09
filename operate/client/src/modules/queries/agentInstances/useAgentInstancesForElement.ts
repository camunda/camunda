/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {agentInstancesSearchOptions} from './agentInstancesSearch';
import type {QueryAgentInstancesResponseBody} from '@camunda/camunda-api-zod-schemas/8.10';

type QueryOptions<T> = {
  processInstanceKey: string;
  elementId: string;
  elementInstanceKey?: string | null;
  enabled?: boolean;
  enablePeriodicRefetch?: boolean;
  select?: (result: QueryAgentInstancesResponseBody) => T;
};

const useAgentInstancesForElement = <T = QueryAgentInstancesResponseBody>(
  options: QueryOptions<T>,
) => {
  return useQuery({
    ...agentInstancesSearchOptions({
      payload: {
        sort: [{field: 'creationDate', order: 'desc'}],
        page: {limit: 15},
        filter: {
          processInstanceKey: options.processInstanceKey,
          elementId: options.elementId,
          elementInstanceKeys: options.elementInstanceKey
            ? [options.elementInstanceKey]
            : undefined,
        },
      },
    }),
    select: options.select,
    enabled: options.enabled,
    refetchInterval: options.enablePeriodicRefetch ? 5000 : undefined,
    staleTime: 5000,
  });
};

export {useAgentInstancesForElement};

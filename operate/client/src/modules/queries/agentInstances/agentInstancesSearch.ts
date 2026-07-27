/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {queryOptions} from '@tanstack/react-query';
import type {QueryAgentInstancesRequestBody} from '@camunda/camunda-api-zod-schemas/8.10';
import {searchAgentInstances} from 'modules/api/v2/agentInstances/searchAgentInstances';
import {queryKeys} from '../queryKeys';

const agentInstancesSearchOptions = (options: {
  payload: QueryAgentInstancesRequestBody;
  loadAllItems?: boolean;
}) => {
  const {payload, loadAllItems} = options;
  return queryOptions({
    queryKey: queryKeys.agentInstances.search(payload, {loadAllItems}),
    queryFn: async ({signal}) => {
      const {response, error} = await searchAgentInstances(payload, signal);
      if (response === null) {
        throw error;
      }

      if (!loadAllItems || response.page.totalItems <= response.items.length) {
        return response;
      }

      const {response: remaining, error: remainingError} =
        await searchAgentInstances(
          {
            ...payload,
            page: {
              from: response.items.length,
              limit: response.page.totalItems,
            },
          },
          signal,
        );
      if (remainingError) {
        throw remainingError;
      }

      return {...response, items: response.items.concat(remaining.items)};
    },
  });
};

export {agentInstancesSearchOptions};

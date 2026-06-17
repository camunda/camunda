/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import type {
  QuerySortOrder,
  SearchAgentInstanceHistoryRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {searchAgentInstanceHistory} from 'modules/api/v2/agentInstances/searchAgentInstanceHistory';
import {queryKeys} from '../queryKeys';

const useAgentInstanceHistory = (
  agentInstanceKey: string,
  options?: {enablePeriodicRefetch?: boolean; sortOrder?: QuerySortOrder},
) => {
  const historyPayload: SearchAgentInstanceHistoryRequestBody = {
    sort: [{field: 'producedAt', order: options?.sortOrder ?? 'desc'}],
    filter: {commitStatus: 'COMMITTED'},
  };

  return useQuery({
    queryKey: queryKeys.agentInstanceHistory.search(
      agentInstanceKey,
      historyPayload,
    ),
    queryFn: async ({signal}) => {
      const {response, error} = await searchAgentInstanceHistory(
        agentInstanceKey,
        historyPayload,
        signal,
      );
      if (response !== null) {
        return response;
      }
      throw error;
    },
    refetchInterval: options?.enablePeriodicRefetch ? 5000 : undefined,
  });
};

export {useAgentInstanceHistory};

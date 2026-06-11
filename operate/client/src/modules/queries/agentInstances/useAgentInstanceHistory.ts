/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {skipToken, useQuery} from '@tanstack/react-query';
import type {SearchAgentInstanceHistoryRequestBody} from '@camunda/camunda-api-zod-schemas/8.10';
import {searchAgentInstanceHistory} from 'modules/api/v2/agentInstances/searchAgentInstanceHistory';
import {queryKeys} from '../queryKeys';

const HISTORY_PAYLOAD: SearchAgentInstanceHistoryRequestBody = {
  sort: [{field: 'producedAt', order: 'desc'}],
  filter: {commitStatus: 'COMMITTED'},
};

const useAgentInstanceHistory = (
  agentInstanceKey: string | undefined,
  options?: {enablePeriodicRefetch?: boolean},
) => {
  return useQuery({
    queryKey: queryKeys.agentInstanceHistory.search(
      agentInstanceKey ?? '',
      HISTORY_PAYLOAD,
    ),
    queryFn: agentInstanceKey
      ? async ({signal}) => {
          const {response, error} = await searchAgentInstanceHistory(
            agentInstanceKey,
            HISTORY_PAYLOAD,
            signal,
          );
          if (response !== null) {
            return response;
          }
          throw error;
        }
      : skipToken,
    refetchInterval: options?.enablePeriodicRefetch ? 5000 : undefined,
  });
};

export {useAgentInstanceHistory};

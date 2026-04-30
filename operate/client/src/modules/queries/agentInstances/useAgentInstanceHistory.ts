/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {skipToken, useQuery} from '@tanstack/react-query';
import {searchAgentInstanceHistory} from 'modules/api/v2/agentInstances/searchAgentInstanceHistory';
import {queryKeys} from '../queryKeys';
import type {
  SearchAgentInstanceHistoryRequestBody,
  SearchAgentInstanceHistoryResponseBody,
} from './types';

const useAgentInstanceHistory = (
  agentInstanceKey: string | undefined,
  payload: SearchAgentInstanceHistoryRequestBody = {},
) => {
  return useQuery<SearchAgentInstanceHistoryResponseBody>({
    queryKey: queryKeys.agentInstances.historySearch(
      agentInstanceKey ?? '',
      payload,
    ),
    queryFn: agentInstanceKey
      ? async () => {
          const {response, error} = await searchAgentInstanceHistory(
            agentInstanceKey,
            payload,
          );
          if (response !== null) {
            return response;
          }
          throw error;
        }
      : skipToken,
  });
};

export {useAgentInstanceHistory};

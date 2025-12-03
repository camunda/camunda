/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  QueryAuditLogsRequestBody,
  QueryAuditLogsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.9';
import {useQuery, type UseQueryResult} from '@tanstack/react-query';
import {queryAuditLogs} from 'modules/api/v2/auditLog/queryAuditLogs';
import type {RequestError} from '../../request';
import {queryKeys} from '../queryKeys';

type QueryOptions<T> = {
  enabled?: boolean;
  select?: (result: QueryAuditLogsResponseBody) => T;
};

const useAuditLogs = <T = QueryAuditLogsResponseBody>(
  payload: QueryAuditLogsRequestBody,
  options?: QueryOptions<T>,
): UseQueryResult<T, RequestError> => {
  return useQuery({
    queryKey: queryKeys.auditLogs.search(payload),
    queryFn: async () => {
      const {response, error} = await queryAuditLogs(payload);
      if (response !== null) {
        return response;
      }
      throw error;
    },
    enabled: options?.enabled,
    select: options?.select,
    staleTime: 5000,
    refetchInterval: 5000,
  });
};

export {useAuditLogs};

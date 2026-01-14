/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {infiniteQueryOptions} from '@tanstack/react-query';
import {api} from 'v2/api';
import {request} from 'common/api/request';
import type {QueryUserTaskAuditLogsResponseBody} from '@camunda/camunda-api-zod-schemas/8.9';

const MAX_AUDIT_LOGS_PER_REQUEST = 50;

function getUserTaskAuditLogsQueryOptions(userTaskKey: string) {
  return infiniteQueryOptions({
    queryKey: ['userTaskAuditLogs', userTaskKey],
    queryFn: async ({pageParam}) => {
      const {response, error} = await request(
        api.queryUserTaskAuditLogs({
          userTaskKey,
          page: {
            from: pageParam,
            limit: MAX_AUDIT_LOGS_PER_REQUEST,
          },
        }),
      );

      if (response !== null) {
        return response.json() as Promise<QueryUserTaskAuditLogsResponseBody>;
      }

      throw error;
    },
    initialPageParam: 0,
    getNextPageParam: (lastPage, _, lastPageParam) => {
      const {page} = lastPage;
      const nextPage = lastPageParam + MAX_AUDIT_LOGS_PER_REQUEST;

      if (nextPage > page.totalItems) {
        return null;
      }

      return nextPage;
    },
    getPreviousPageParam: (_, __, firstPageParam) => {
      const previousPage = firstPageParam - MAX_AUDIT_LOGS_PER_REQUEST;

      if (previousPage < 0) {
        return null;
      }

      return previousPage;
    },
  });
}

export {getUserTaskAuditLogsQueryOptions};

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {queryOptions} from '@tanstack/react-query';
import {api} from 'v2/api';
import {request} from 'common/api/request';
import type {GetAuditLogResponseBody} from '@camunda/camunda-api-zod-schemas/8.9';

function getAuditLogQueryKey(auditLogKey: string) {
  return ['auditLog', auditLogKey];
}

function getAuditLogQueryOptions(auditLogKey: string) {
  return queryOptions({
    queryKey: getAuditLogQueryKey(auditLogKey),
    queryFn: async () => {
      const {response, error} = await request(api.getAuditLog({auditLogKey}));

      if (response !== null) {
        return response.json() as Promise<GetAuditLogResponseBody>;
      }

      throw error;
    },
  });
}

export {getAuditLogQueryOptions, getAuditLogQueryKey};

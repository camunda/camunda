/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {
  searchAuditLog,
  type AuditLogSearchRequest,
} from 'modules/api/v2/auditLog/searchAuditLog';

const AUDIT_LOG_QUERY_KEY = 'auditLog';

const useAuditLog = (request: AuditLogSearchRequest) => {
  return useQuery({
    queryKey: [AUDIT_LOG_QUERY_KEY, request],
    queryFn: async () => {
      const {response, error} = await searchAuditLog(request);

      if (response !== null) {
        return response;
      }

      throw error;
    },
  });
};

export {useAuditLog, AUDIT_LOG_QUERY_KEY};

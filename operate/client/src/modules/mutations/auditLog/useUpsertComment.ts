/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation, useQueryClient} from '@tanstack/react-query';
import {
  upsertComment,
  type UpsertCommentRequest,
} from 'modules/api/v2/auditLog/upsertComment';
import {AUDIT_LOG_QUERY_KEY} from 'modules/queries/auditLog/useAuditLog';

const useUpsertComment = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (request: UpsertCommentRequest) => {
      const {response, error} = await upsertComment(request);

      if (response !== null) {
        return response;
      }

      throw error;
    },
    onSuccess: () => {
      // Invalidate all audit log queries to refetch data
      queryClient.invalidateQueries({queryKey: [AUDIT_LOG_QUERY_KEY]});
    },
  });
};

export {useUpsertComment};

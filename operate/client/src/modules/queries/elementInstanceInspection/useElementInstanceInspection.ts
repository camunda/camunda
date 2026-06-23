/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import type {
  QueryElementInstanceInspectionRequestBody,
  QueryElementInstanceInspectionResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {searchElementInstanceInspection} from 'modules/api/v2/elementInstanceInspection/searchElementInstanceInspection';
import {useIsProcessInstanceRunning} from 'modules/queries/processInstance/useIsProcessInstanceRunning';
import {queryKeys} from '../queryKeys';

const MAX_WAIT_STATES = 1000;

type UseElementInstanceInspectionParams = {
  processInstanceKey: string;
  elementInstanceKey?: string;
  enabled?: boolean;
};

const useElementInstanceInspection = (
  params: UseElementInstanceInspectionParams,
) => {
  const {processInstanceKey, elementInstanceKey, enabled = true} = params;
  const {data: isProcessInstanceRunning} = useIsProcessInstanceRunning();

  const payload: QueryElementInstanceInspectionRequestBody = {
    filter: {
      processInstanceKey,
      ...(elementInstanceKey ? {elementInstanceKey} : {}),
    },
    page: {limit: MAX_WAIT_STATES},
  };

  return useQuery<QueryElementInstanceInspectionResponseBody>({
    queryKey: queryKeys.elementInstanceInspection.search(payload),
    queryFn: async ({signal}) => {
      const {response, error} = await searchElementInstanceInspection(
        payload,
        signal,
      );
      if (response !== null) {
        return response;
      }
      throw error;
    },
    enabled: enabled && !!processInstanceKey,
    staleTime: 10000,
    refetchInterval: () => (isProcessInstanceRunning ? 5000 : undefined),
  });
};

export {useElementInstanceInspection, MAX_WAIT_STATES};

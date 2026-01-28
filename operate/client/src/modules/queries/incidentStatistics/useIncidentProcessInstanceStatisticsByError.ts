/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {fetchIncidentProcessInstanceStatisticsByError} from 'modules/api/v2/incidents/fetchIncidentProcessInstanceStatisticsByError';
import {queryKeys} from '../queryKeys';
import type {GetIncidentProcessInstanceStatisticsByErrorRequestBody} from '@camunda/camunda-api-zod-schemas/8.9';

type UseIncidentProcessInstanceStatisticsByErrorOptions = {
  payload?: GetIncidentProcessInstanceStatisticsByErrorRequestBody;
  enabled?: boolean;
};

const useIncidentProcessInstanceStatisticsByError = ({
  payload,
  enabled = true,
}: UseIncidentProcessInstanceStatisticsByErrorOptions = {}) => {
  const payloadWithDefaults: GetIncidentProcessInstanceStatisticsByErrorRequestBody =
    {
      sort: [
        {field: 'activeInstancesWithErrorCount', order: 'desc'},
        {field: 'errorMessage', order: 'asc'},
      ],
      ...payload,
    };
  return useQuery({
    queryKey:
      queryKeys.incidentProcessInstanceStatisticsByError.get(
        payloadWithDefaults,
      ),
    queryFn: async () => {
      const {response, error} =
        await fetchIncidentProcessInstanceStatisticsByError(
          payloadWithDefaults,
        );

      if (response !== null) {
        return response;
      }
      throw error;
    },
    enabled,
    refetchInterval: 5000,
  });
};

export {useIncidentProcessInstanceStatisticsByError};

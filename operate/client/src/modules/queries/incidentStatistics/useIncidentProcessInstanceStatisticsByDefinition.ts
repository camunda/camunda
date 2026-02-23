/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {fetchIncidentProcessInstanceStatisticsByDefinition} from 'modules/api/v2/incidents/fetchIncidentProcessInstanceStatisticsByDefinition';
import {queryKeys} from '../queryKeys';
import type {GetIncidentProcessInstanceStatisticsByDefinitionRequestBody} from '@camunda/camunda-api-zod-schemas/8.9';

type UseIncidentProcessInstanceStatisticsByDefinitionOptions = {
  payload?: GetIncidentProcessInstanceStatisticsByDefinitionRequestBody;
  enabled?: boolean;
};

const useIncidentProcessInstanceStatisticsByDefinition = ({
  payload,
  enabled = true,
}: UseIncidentProcessInstanceStatisticsByDefinitionOptions = {}) => {
  const payloadWithDefaults: GetIncidentProcessInstanceStatisticsByDefinitionRequestBody =
    {
      sort: [
        {field: 'activeInstancesWithErrorCount', order: 'desc'},
        {field: 'processDefinitionKey', order: 'desc'},
        {field: 'tenantId', order: 'desc'},
      ],
      ...payload,
    };
  return useQuery({
    queryKey:
      queryKeys.incidentProcessInstanceStatisticsByDefinition.get(
        payloadWithDefaults,
      ),
    queryFn: async () => {
      const {response, error} =
        await fetchIncidentProcessInstanceStatisticsByDefinition(
          payloadWithDefaults,
        );

      if (response !== null) {
        return response;
      }
      throw error;
    },
    enabled,
  });
};

export {useIncidentProcessInstanceStatisticsByDefinition};

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';

import {searchIncidentsByProcessInstance} from 'modules/api/v2/incidents/searchIncidentsByProcessInstance';
const INCIDENTS_SEARCH_QUERY_KEY = 'incidentsSearch';

const useGetIncidentsByProcessInstance = (
  processInstanceKey: string,
  elementInstanceKey?: string,
  options: {enabled: boolean} = {enabled: true},
) => {
  return useQuery({
    queryKey: [
      INCIDENTS_SEARCH_QUERY_KEY,
      processInstanceKey,
      elementInstanceKey,
    ],
    queryFn: async () => {
      const {response, error} = await searchIncidentsByProcessInstance({
        processInstanceKey,
      });

      if (response !== null) {
        const incidents = elementInstanceKey
          ? response.items.filter(
              (incident) => incident.elementInstanceKey === elementInstanceKey,
            )
          : response.items;

        return incidents.length > 0 ? incidents : null;
      }

      throw error;
    },
    ...options,
  });
};

export {useGetIncidentsByProcessInstance};

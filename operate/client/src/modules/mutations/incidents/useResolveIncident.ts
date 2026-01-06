/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation, useQueryClient} from '@tanstack/react-query';
import {resolveIncident} from 'modules/api/v2/incidents/resolveIncident';
import {fetchIncident} from 'modules/api/v2/incidents/fetchIncident';
import {updateJob} from 'modules/api/v2/jobs/updateJob';
import {queryKeys} from 'modules/queries/queryKeys';

type ResolveIncidentOptions = {
  incidentKey: string;
  jobKey?: string;
  onSuccess?: () => Promise<unknown> | unknown;
  onError?: (error: {
    status: number;
    statusText: string;
  }) => Promise<unknown> | unknown;
};

function useResolveIncident(options: ResolveIncidentOptions) {
  const queryClient = useQueryClient();

  return useMutation<void, {status: number; statusText: string}>({
    scope: {id: options.incidentKey},
    onError: (error) => {
      return options.onError?.(error);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({queryKey: queryKeys.incidents.search()});
      return options.onSuccess?.();
    },
    mutationFn: async () => {
      const {incidentKey, jobKey} = options;

      if (jobKey) {
        const response = await updateJob(jobKey, {retries: 1});
        if (!response.ok) {
          throw {status: response.status, statusText: response.statusText};
        }
      }

      const response = await resolveIncident(incidentKey);
      if (!response.ok) {
        throw {status: response.status, statusText: response.statusText};
      }

      await queryClient.fetchQuery({
        queryKey: queryKeys.incidents.get(incidentKey),
        queryFn: async () => {
          const {response: incident, error} = await fetchIncident(incidentKey);

          if (error !== null) {
            throw error;
          }

          if (incident.state === 'ACTIVE') {
            throw new Error('Incident is still active');
          }

          return incident;
        },
        retry: true,
      });
    },
  });
}

export {useResolveIncident};

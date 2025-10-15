/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation, useQueryClient} from '@tanstack/react-query';
import {resolveIncident} from 'modules/api/v2/incidents/resolveIncident';
import {updateJob} from 'modules/api/v2/jobs/updateJob';
import {queryKeys} from 'modules/queries/queryKeys';

function useResolveIncident(incidentKey: string, jobKey?: string) {
  const queryClient = useQueryClient();

  return useMutation<void, {status: number; statusText: string}>({
    scope: {id: incidentKey},
    onSuccess: () => {
      queryClient.invalidateQueries({queryKey: queryKeys.incidents.search()});
    },
    mutationFn: async () => {
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
    },
  });
}

export {useResolveIncident};

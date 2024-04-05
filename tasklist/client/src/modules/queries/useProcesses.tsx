/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useQuery, UseQueryOptions} from '@tanstack/react-query';
import {api} from 'modules/api';
import {request, RequestError} from 'modules/request';
import {tracking} from 'modules/tracking';
import {Process} from 'modules/types';

type Data = {
  processes: Process[];
  query?: string;
};

type Params = {
  query?: string;
  tenantId?: string;
  isStartedByForm?: boolean;
};

function useProcesses(
  params: Params,
  options: Pick<
    UseQueryOptions<Data, RequestError>,
    'refetchInterval' | 'enabled' | 'keepPreviousData'
  > = {},
) {
  const {query, tenantId, isStartedByForm} = params;
  return useQuery<Data, RequestError>({
    queryKey: ['processes', query, tenantId, isStartedByForm],
    queryFn: async () => {
      const {response, error} = await request(
        api.getProcesses({query, tenantId, isStartedByForm}),
      );

      if (response !== null) {
        return {
          processes: await response.json(),
          query,
        };
      }

      throw error ?? new Error('Failed to fetch processes');
    },
    onSuccess: (data) => {
      tracking.track({
        eventName: 'processes-loaded',
        filter: data.query ?? '',
        count: data.processes.length,
      });
    },
    ...options,
  });
}

function createMockProcess(id: string): Process {
  return {
    id,
    name: `Process ${id}`,
    bpmnProcessId: `bpmn-process-id-${id}`,
    startEventFormId: `form-id-${id}`,
    sortValues: ['1'],
    version: 1,
  };
}

export {useProcesses, createMockProcess};

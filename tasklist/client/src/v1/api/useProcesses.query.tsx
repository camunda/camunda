/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery, type UseQueryOptions} from '@tanstack/react-query';
import {api} from 'v1/api';
import {request, type RequestError} from 'common/api/request';
import {tracking} from 'common/tracking';
import type {Process} from 'v1/api/types';

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
    'refetchInterval' | 'enabled' | 'placeholderData'
  > = {},
) {
  const {query, tenantId, isStartedByForm} = params;
  return useQuery<Data, RequestError>({
    queryKey: ['processes', query, tenantId, isStartedByForm],
    queryFn: async () => {
      const {response, error} = await request(
        api.v1.getProcesses({query, tenantId, isStartedByForm}),
      );

      if (response !== null) {
        const processes = await response.json();
        tracking.track({
          eventName: 'processes-loaded',
          filter: query ?? '',
          count: processes.length,
        });
        return {
          processes,
          query,
        };
      }

      throw error ?? new Error('Failed to fetch processes');
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
    bpmnXml: null,
  };
}

export {useProcesses, createMockProcess};

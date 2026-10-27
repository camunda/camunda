/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {renderHook, waitFor} from '@testing-library/react';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockSearchElementInstanceInspection} from 'modules/mocks/api/v2/elementInstanceInspection/searchElementInstanceInspection';
import {searchResult} from 'modules/testUtils';
import type {ElementInstanceInspection} from '@camunda/camunda-api-zod-schemas/8.10';
import {queryKeys} from 'modules/queries/queryKeys';
import {
  useElementInstanceInspection,
  MAX_WAIT_STATES,
} from './useElementInstanceInspection';

const PROCESS_INSTANCE_KEY = '123';

const waitState: ElementInstanceInspection = {
  rootProcessInstanceKey: PROCESS_INSTANCE_KEY,
  processInstanceKey: PROCESS_INSTANCE_KEY,
  elementInstanceKey: '456',
  elementId: 'Task_1',
  elementType: 'SERVICE_TASK',
  tenantId: '<default>',
  bpmnProcessId: 'process-1',
  details: {
    waitStateType: 'JOB',
    jobKey: '789',
    jobType: 'customJob',
    jobKind: 'BPMN_ELEMENT',
    listenerEventType: null,
    retries: null,
  },
};

describe('useElementInstanceInspection', () => {
  it('should return the fetched wait states when enabled', async () => {
    mockSearchElementInstanceInspection().withSuccess(
      searchResult([waitState]),
    );

    const {result} = renderHook(
      () =>
        useElementInstanceInspection({
          processInstanceKey: PROCESS_INSTANCE_KEY,
          enabled: true,
        }),
      {
        wrapper: ({children}) => (
          <QueryClientProvider client={getMockQueryClient()}>
            {children}
          </QueryClientProvider>
        ),
      },
    );

    await waitFor(() =>
      expect(result.current.data?.items).toEqual([waitState]),
    );
  });

  it('should not surface stale cached wait states when disabled', () => {
    const queryClient = getMockQueryClient();
    queryClient.setQueryData(
      queryKeys.elementInstanceInspection.search({
        filter: {processInstanceKey: PROCESS_INSTANCE_KEY},
        page: {limit: MAX_WAIT_STATES},
      }),
      searchResult([waitState]),
    );

    const {result} = renderHook(
      () =>
        useElementInstanceInspection({
          processInstanceKey: PROCESS_INSTANCE_KEY,
          enabled: false,
        }),
      {
        wrapper: ({children}) => (
          <QueryClientProvider client={queryClient}>
            {children}
          </QueryClientProvider>
        ),
      },
    );

    expect(result.current.data?.items).toEqual([]);
  });
});

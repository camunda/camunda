/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ElementInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import {QueryClientProvider} from '@tanstack/react-query';
import {renderHook, waitFor} from '@testing-library/react';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {Paths} from 'modules/Routes';
import {searchResult} from 'modules/testUtils';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {useElementSelectionInstanceKey} from './useElementSelectionInstanceKey';
import {mockFetchElementInstance} from 'modules/mocks/api/v2/elementInstances/fetchElementInstance';

const PROCESS_INSTANCE_KEY = '123';
const mockElementInstance: ElementInstance = {
  elementInstanceKey: '2251799813699889',
  elementId: 'service-task-1',
  elementName: 'Service Task',
  type: 'SERVICE_TASK',
  state: 'ACTIVE',
  startDate: '2018-06-21',
  processDefinitionId: 'process-def-1',
  processInstanceKey: PROCESS_INSTANCE_KEY,
  processDefinitionKey: '2',
  hasIncident: false,
  tenantId: '<default>',
};

const getWrapper = (initialSearchParams?: Record<string, string>) => {
  const Wrapper = ({children}: {children: React.ReactNode}) => {
    const searchParams = new URLSearchParams(initialSearchParams);

    return (
      <QueryClientProvider client={getMockQueryClient()}>
        <MemoryRouter
          initialEntries={[
            `${Paths.processInstance(PROCESS_INSTANCE_KEY)}?${searchParams.toString()}`,
          ]}
        >
          <Routes>
            <Route path={Paths.processInstance()} element={children} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );
  };
  return Wrapper;
};

describe('useElementSelectionInstanceKey', () => {
  it('should return the process instance key if no element is selected', () => {
    const {result} = renderHook(() => useElementSelectionInstanceKey(), {
      wrapper: getWrapper(),
    });

    expect(result.current).toBe('123');
  });

  it('should return the selected element instance key when an instance is selected', () => {
    mockFetchElementInstance(':key').withSuccess(mockElementInstance);

    const {result} = renderHook(() => useElementSelectionInstanceKey(), {
      wrapper: getWrapper({elementInstanceKey: '456'}),
    });

    expect(result.current).toBe('456');
  });

  it('should return the resolved instance key when an element is selected', async () => {
    mockSearchElementInstances().withSuccess(
      searchResult([mockElementInstance]),
    );

    const {result} = renderHook(() => useElementSelectionInstanceKey(), {
      wrapper: getWrapper({elementId: '789'}),
    });

    await waitFor(() =>
      expect(result.current).toBe(mockElementInstance.elementInstanceKey),
    );
  });

  it('should null when a selected instance key cannot be resolved', async () => {
    mockSearchElementInstances().withServerError(404);

    const {result} = renderHook(() => useElementSelectionInstanceKey(), {
      wrapper: getWrapper({elementId: '789'}),
    });

    await waitFor(() => expect(result.current).toBeNull());
  });
});

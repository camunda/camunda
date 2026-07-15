/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// setupTests.tsx stubs out InfiniteScroller with a passthrough fragment, which
// hides bugs in how its children are given a ref. Use the real implementation
// here so a broken ref hookup (e.g. attaching it to a non-forwardRef
// component) actually surfaces.
vi.mock('modules/components/InfiniteScroller', async () => {
  return await vi.importActual('modules/components/InfiniteScroller');
});

import {render, screen} from 'modules/testing-library';
import {QueryClientProvider} from '@tanstack/react-query';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import type {
  ElementInstance,
  QueryElementInstancesResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.10';
import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import {FilteredElementInstancesList} from './index';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {Paths} from 'modules/Routes';

const PROCESS_INSTANCE_KEY = '1';

const createMockElementInstance = (
  overrides: Partial<ElementInstance> = {},
): ElementInstance => ({
  elementInstanceKey: '100',
  elementId: 'order_task',
  elementName: 'Order Task',
  type: 'SERVICE_TASK',
  state: 'ACTIVE',
  startDate: '2023-01-01T10:00:00.000Z',
  processDefinitionKey: '2',
  processDefinitionId: 'test-process',
  processInstanceKey: PROCESS_INSTANCE_KEY,
  hasIncident: false,
  tenantId: '<default>',
  endDate: null,
  rootProcessInstanceKey: null,
  incidentKey: null,
  ...overrides,
});

const mockResponse = (
  items: ElementInstance[],
  totalItems: number,
): QueryElementInstancesResponseBody => ({
  items,
  page: {
    totalItems,
    startCursor: null,
    endCursor: null,
    hasMoreTotalItems: false,
  },
});

const businessObjects: BusinessObjects = {};

const Wrapper: React.FC<{children: React.ReactNode}> = ({children}) => (
  <MemoryRouter initialEntries={[Paths.processInstance(PROCESS_INSTANCE_KEY)]}>
    <ProcessDefinitionKeyContext.Provider value="2">
      <QueryClientProvider client={getMockQueryClient()}>
        <Routes>
          <Route path={Paths.processInstance()} element={children} />
        </Routes>
      </QueryClientProvider>
    </ProcessDefinitionKeyContext.Provider>
  </MemoryRouter>
);

class MockIntersectionObserver implements IntersectionObserver {
  readonly root = null;
  readonly rootMargin = '';
  readonly thresholds: number[] = [];
  observe = vi.fn();
  unobserve = vi.fn();
  disconnect = vi.fn();
  takeRecords = () => [];
}

describe('<FilteredElementInstancesList /> with the real InfiniteScroller', () => {
  it('attaches the scroll observer to the rendered results instead of warning about an invalid ref', async () => {
    const observeSpy = vi.fn();
    class ObservingIntersectionObserver extends MockIntersectionObserver {
      observe = observeSpy;
    }
    vi.stubGlobal('IntersectionObserver', ObservingIntersectionObserver);
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    mockSearchElementInstances().withSuccess(
      mockResponse([createMockElementInstance({elementInstanceKey: '100'})], 1),
    );

    render(
      <FilteredElementInstancesList
        searchText="order"
        processInstanceKey={PROCESS_INSTANCE_KEY}
        businessObjects={businessObjects}
      />,
      {wrapper: Wrapper},
    );

    expect(await screen.findByTestId('search-result-100')).toBeInTheDocument();

    expect(
      errorSpy.mock.calls.some((call) =>
        String(call[0]).includes('Function components cannot be given refs'),
      ),
    ).toBe(false);
    expect(observeSpy).toHaveBeenCalled();
  });
});

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {renderHook, waitFor} from '@testing-library/react';
import {QueryClientProvider} from '@tanstack/react-query';
import {MemoryRouter, Routes, Route} from 'react-router-dom';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockSearchAgentInstances} from 'modules/mocks/api/v2/agentInstances/searchAgentInstances';
import {mockAgentInstance} from 'modules/mocks/mockAgentInstance';
import {searchResult} from 'modules/testUtils';
import {Paths} from 'modules/Routes';
import {useAgentInstancesStatusPerElement} from './agentInstances';

const PROCESS_INSTANCE_ID = '123';

const getWrapper = (processInstanceId = PROCESS_INSTANCE_ID) => {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => (
    <QueryClientProvider client={getMockQueryClient()}>
      <MemoryRouter initialEntries={[Paths.processInstance(processInstanceId)]}>
        <Routes>
          <Route path={Paths.processInstance()} element={children} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
  return Wrapper;
};

describe('useAgentInstancesStatusPerElement', () => {
  it('should return an empty result when there are no agent instances', async () => {
    mockSearchAgentInstances().withSuccess(searchResult([]));

    const {result} = renderHook(() => useAgentInstancesStatusPerElement(), {
      wrapper: getWrapper(),
    });

    await waitFor(() =>
      expect(result.current.agentInstancesStatusMap.size).toBe(0),
    );
    expect(result.current.elementsWithAgent.size).toBe(0);
  });

  it('should map an agent instance to its element', async () => {
    mockSearchAgentInstances().withSuccess(
      searchResult([
        mockAgentInstance({
          agentInstanceKey: 'agent-1',
          elementId: 'Activity_agent',
          status: 'THINKING',
        }),
      ]),
    );

    const {result} = renderHook(() => useAgentInstancesStatusPerElement(), {
      wrapper: getWrapper(),
    });

    await waitFor(() =>
      expect(result.current.agentInstancesStatusMap.size).toBe(1),
    );
    expect(
      result.current.agentInstancesStatusMap.get('Activity_agent'),
    ).toEqual({
      agentInstanceKey: 'agent-1',
      status: 'THINKING',
      additionalActiveCount: 0,
    });
    expect(result.current.elementsWithAgent).toContain('Activity_agent');
  });

  it('should map agent instances on different elements to separate entries', async () => {
    mockSearchAgentInstances().withSuccess(
      searchResult([
        mockAgentInstance({
          agentInstanceKey: 'agent-1',
          elementId: 'Activity_a',
          status: 'THINKING',
        }),
        mockAgentInstance({
          agentInstanceKey: 'agent-2',
          elementId: 'Activity_b',
          status: 'TOOL_CALLING',
        }),
      ]),
    );

    const {result} = renderHook(() => useAgentInstancesStatusPerElement(), {
      wrapper: getWrapper(),
    });

    await waitFor(() =>
      expect(result.current.agentInstancesStatusMap.size).toBe(2),
    );
    expect(
      result.current.agentInstancesStatusMap.get('Activity_a'),
    ).toMatchObject({agentInstanceKey: 'agent-1', additionalActiveCount: 0});
    expect(
      result.current.agentInstancesStatusMap.get('Activity_b'),
    ).toMatchObject({agentInstanceKey: 'agent-2', additionalActiveCount: 0});
    expect(result.current.elementsWithAgent).toContain('Activity_a');
    expect(result.current.elementsWithAgent).toContain('Activity_b');
  });

  it('should count additional active agents sharing an element', async () => {
    mockSearchAgentInstances().withSuccess(
      searchResult([
        mockAgentInstance({
          agentInstanceKey: 'agent-1',
          elementId: 'Activity_agent',
          status: 'THINKING',
        }),
        mockAgentInstance({
          agentInstanceKey: 'agent-2',
          elementId: 'Activity_agent',
          status: 'THINKING',
        }),
        mockAgentInstance({
          agentInstanceKey: 'agent-3',
          elementId: 'Activity_agent',
          status: 'THINKING',
        }),
      ]),
    );

    const {result} = renderHook(() => useAgentInstancesStatusPerElement(), {
      wrapper: getWrapper(),
    });

    await waitFor(() =>
      expect(result.current.agentInstancesStatusMap.size).toBe(1),
    );
    expect(
      result.current.agentInstancesStatusMap.get('Activity_agent'),
    ).toMatchObject({additionalActiveCount: 2});
  });

  it('should surface the most important status among agents on the same element', async () => {
    mockSearchAgentInstances().withSuccess(
      searchResult([
        mockAgentInstance({
          agentInstanceKey: 'agent-1',
          elementId: 'Activity_agent',
          status: 'TOOL_CALLING',
        }),
        mockAgentInstance({
          agentInstanceKey: 'agent-2',
          elementId: 'Activity_agent',
          status: 'INITIALIZING',
        }),
      ]),
    );

    const {result} = renderHook(() => useAgentInstancesStatusPerElement(), {
      wrapper: getWrapper(),
    });

    await waitFor(() =>
      expect(result.current.agentInstancesStatusMap.size).toBe(1),
    );
    expect(
      result.current.agentInstancesStatusMap.get('Activity_agent'),
    ).toMatchObject({
      agentInstanceKey: 'agent-2',
      status: 'INITIALIZING',
    });
  });

  it('should prefer TOOL_DISCOVERY over TOOL_CALLING', async () => {
    mockSearchAgentInstances().withSuccess(
      searchResult([
        mockAgentInstance({
          agentInstanceKey: 'agent-1',
          elementId: 'Activity_agent',
          status: 'TOOL_DISCOVERY',
        }),
        mockAgentInstance({
          agentInstanceKey: 'agent-2',
          elementId: 'Activity_agent',
          status: 'TOOL_CALLING',
        }),
      ]),
    );

    const {result} = renderHook(() => useAgentInstancesStatusPerElement(), {
      wrapper: getWrapper(),
    });

    await waitFor(() =>
      expect(result.current.agentInstancesStatusMap.size).toBe(1),
    );
    expect(
      result.current.agentInstancesStatusMap.get('Activity_agent'),
    ).toMatchObject({
      agentInstanceKey: 'agent-1',
      status: 'TOOL_DISCOVERY',
    });
  });

  it('should prefer THINKING over TOOL_DISCOVERY', async () => {
    mockSearchAgentInstances().withSuccess(
      searchResult([
        mockAgentInstance({
          agentInstanceKey: 'agent-1',
          elementId: 'Activity_agent',
          status: 'THINKING',
        }),
        mockAgentInstance({
          agentInstanceKey: 'agent-2',
          elementId: 'Activity_agent',
          status: 'TOOL_DISCOVERY',
        }),
      ]),
    );

    const {result} = renderHook(() => useAgentInstancesStatusPerElement(), {
      wrapper: getWrapper(),
    });

    await waitFor(() =>
      expect(result.current.agentInstancesStatusMap.size).toBe(1),
    );
    expect(
      result.current.agentInstancesStatusMap.get('Activity_agent'),
    ).toMatchObject({
      agentInstanceKey: 'agent-1',
      status: 'THINKING',
    });
  });

  it('should prefer INITIALIZING over THINKING', async () => {
    mockSearchAgentInstances().withSuccess(
      searchResult([
        mockAgentInstance({
          agentInstanceKey: 'agent-1',
          elementId: 'Activity_agent',
          status: 'INITIALIZING',
        }),
        mockAgentInstance({
          agentInstanceKey: 'agent-2',
          elementId: 'Activity_agent',
          status: 'THINKING',
        }),
      ]),
    );

    const {result} = renderHook(() => useAgentInstancesStatusPerElement(), {
      wrapper: getWrapper(),
    });

    await waitFor(() =>
      expect(result.current.agentInstancesStatusMap.size).toBe(1),
    );
    expect(
      result.current.agentInstancesStatusMap.get('Activity_agent'),
    ).toMatchObject({
      agentInstanceKey: 'agent-1',
      status: 'INITIALIZING',
    });
  });
});

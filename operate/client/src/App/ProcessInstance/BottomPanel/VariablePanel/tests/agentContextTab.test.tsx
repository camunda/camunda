/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {vi} from 'vitest';
import {render, screen} from 'modules/testing-library';
import {VariablePanel} from '../index';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {Paths} from 'modules/Routes';
import {http, HttpResponse} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {act} from 'react';

vi.mock('modules/hooks/useProcessInstanceElementSelection', () => {
  return {
    useProcessInstanceElementSelection: () => {
      return {
        hasSelection: true,
        selectedElementId: 'AdHoc_1',
        resolvedElementInstance: {
          // minimal shape for this test
          elementInstanceKey: '123',
          type: 'AD_HOC_SUB_PROCESS',
          state: 'ACTIVE',
        },
      };
    },
  };
});

vi.mock('modules/hooks/useElementSelectionInstanceKey', () => {
  return {
    useElementSelectionInstanceKey: () => '123',
  };
});

vi.mock('App/ProcessInstance/useProcessInstancePageParams', () => {
  return {
    useProcessInstancePageParams: () => ({processInstanceId: '999'}),
  };
});

vi.mock('modules/queries/agentContext/useHasAgentContext', () => {
  return {
    useHasAgentContext: () => ({data: {hasAgentContext: true}}),
  };
});

vi.mock('modules/queries/jobs/useJobs', () => {
  return {
    useJobs: () => ({
      data: [],
      fetchNextPage: vi.fn(),
      fetchPreviousPage: vi.fn(),
      hasNextPage: false,
      hasPreviousPage: false,
    }),
  };
});

vi.mock('modules/queries/agentContext/useAgentContextVariable', () => {
  return {
    useAgentContextVariable: () => ({
      isLoading: false,
      isError: false,
      data: {
        parsed: {
          state: 'READY',
          conversation: {messages: []},
        },
      },
    }),
  };
});

vi.mock('modules/feature-flags', () => {
  return {
    IS_ELEMENT_SELECTION_V2: true,
  };
});

vi.mock('modules/agentContext/confetti/agentTabConfetti', () => {
  return {
    fireAgentTabConfettiOnce: vi.fn(),
  };
});

import {fireAgentTabConfettiOnce} from 'modules/agentContext/confetti/agentTabConfetti';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return (
    <QueryClientProvider client={getMockQueryClient()}>
      <MemoryRouter initialEntries={[Paths.processInstance('999')]}>
        <Routes>
          <Route path={Paths.processInstance()} element={children} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
};

describe('VariablePanel - AI Agent tab', () => {
  it('should keep the AI Agent tab visible once discovered for a selection', async () => {
    let hasAgentContext = true;

    vi.doMock('modules/queries/agentContext/useHasAgentContext', () => {
      return {
        useHasAgentContext: () => ({data: {hasAgentContext}}),
      };
    });

    mockServer.use(
      http.get('/v2/process-instances/:key', () => {
        return HttpResponse.json({
          processInstanceKey: '999',
          state: 'ACTIVE',
          startDate: '2018-06-21',
          processDefinitionKey: '2',
          processDefinitionVersion: 1,
          processDefinitionId: 'someKey',
          tenantId: '<default>',
          processDefinitionName: 'someProcessName',
          hasIncident: false,
        });
      }),
      http.post('/v2/variables/search', () => {
        return HttpResponse.json({
          items: [],
          page: {totalItems: 0, firstSortValues: [], lastSortValues: []},
        });
      }),
    );

    render(<VariablePanel setListenerTabVisibility={vi.fn()} />, {
      wrapper: Wrapper,
    });

    expect(screen.getByRole('tab', {name: 'AI Agent'})).toBeInTheDocument();

    // Simulate a transient refetch where the gate hasn't resolved yet.
    hasAgentContext = false;

    await act(async () => {
      // force a rerender by triggering a state update
      window.dispatchEvent(new Event('resize'));
    });

    // Tab should still be present (sticky)
    expect(screen.getByRole('tab', {name: 'AI Agent'})).toBeInTheDocument();
  });

  it('should show and select the AI Agent tab when agentContext is present on an ad-hoc selection', async () => {
    mockServer.use(
      http.get('/v2/process-instances/:key', () => {
        return HttpResponse.json({
          processInstanceKey: '999',
          state: 'ACTIVE',
          startDate: '2018-06-21',
          processDefinitionKey: '2',
          processDefinitionVersion: 1,
          processDefinitionId: 'someKey',
          tenantId: '<default>',
          processDefinitionName: 'someProcessName',
          hasIncident: false,
        });
      }),
      http.post('/v2/variables/search', () => {
        return HttpResponse.json({
          items: [],
          page: {totalItems: 0, firstSortValues: [], lastSortValues: []},
        });
      }),
    );

    render(<VariablePanel setListenerTabVisibility={vi.fn()} />, {
      wrapper: Wrapper,
    });

    // Tab should exist
    expect(screen.getByRole('tab', {name: 'AI Agent'})).toBeInTheDocument();

    // Since AI Agent tab is inserted first, it should be selected and its title visible
    expect(screen.getByText('Timeline')).toBeInTheDocument();

    // Confetti should be attempted when the tab appears (auto-selected)
    await act(async () => {
      // Allow requestAnimationFrame to run
      await new Promise((r) => requestAnimationFrame(() => r(null)));
    });

    expect(fireAgentTabConfettiOnce).toHaveBeenCalled();
  });
});

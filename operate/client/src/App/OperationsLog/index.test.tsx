/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {OperationsLog} from './index';
import {render, screen, waitFor} from 'modules/testing-library';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {processesStore} from 'modules/stores/processes/processes.list';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {processInstancesStore} from 'modules/stores/processInstances';
import {mockMe} from 'modules/mocks/api/v2/me';
import {mockSearchProcessDefinitions} from 'modules/mocks/api/v2/processDefinitions/searchProcessDefinitions';
import {mockQueryAuditLogs} from 'modules/mocks/api/v2/auditLogs/queryAuditLogs';
import {
  createProcessDefinition,
  createUser,
  searchResult,
} from 'modules/testUtils';

vi.mock('modules/tracking', () => ({
  tracking: {
    track: vi.fn(),
  },
}));

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return (
    <QueryClientProvider client={getMockQueryClient()}>
      <MemoryRouter initialEntries={['/operations-log']}>
        <Routes>
          <Route path="/operations-log" element={children} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
};

describe('OperationsLog', () => {
  beforeEach(() => {
    mockMe().withSuccess(createUser());
    mockSearchProcessDefinitions().withSuccess(
      searchResult([
        createProcessDefinition({name: 'Test Process', version: 1}),
      ]),
    );
    mockSearchProcessDefinitions().withSuccess(
      searchResult([
        createProcessDefinition({name: 'Test Process', version: 1}),
      ]),
    );
    mockQueryAuditLogs().withSuccess({
      items: [],
      page: {totalItems: 0},
    });
  });

  afterEach(() => {
    processesStore.reset();
    processInstancesStore.reset();
    processInstancesSelectionStore.reset();
  });

  it('should render filters and instances table', async () => {
    render(<OperationsLog />, {
      wrapper: Wrapper,
    });

    expect(screen.getByText('Process')).toBeInTheDocument();
    expect(screen.getByText('Operations Log')).toBeInTheDocument();
  });

  it('should fetch processes on mount', async () => {
    const {user} = render(<OperationsLog />, {
      wrapper: Wrapper,
    });

    await waitFor(() => {
      expect(screen.getByRole('combobox', {name: 'Name'})).toBeEnabled();
    });

    await user.click(screen.getByRole('combobox', {name: 'Name'}));

    expect(
      screen.getByRole('option', {name: 'Test Process'}),
    ).toBeInTheDocument();
  });

  it('should set page title to instances', () => {
    render(<OperationsLog />, {
      wrapper: Wrapper,
    });

    expect(document.title).toContain('Operate: Operations Log');
  });
});

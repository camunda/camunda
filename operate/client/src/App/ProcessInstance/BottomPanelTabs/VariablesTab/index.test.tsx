/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {Paths} from 'modules/Routes';
import {VariablesTab} from './index';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockSearchVariables} from 'modules/mocks/api/v2/variables/searchVariables';
import {createVariable} from 'modules/testUtils';

const getWrapper = () => {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => (
    <QueryClientProvider client={getMockQueryClient()}>
      <MemoryRouter initialEntries={[Paths.processInstance('1')]}>
        <Routes>
          <Route path={Paths.processInstance()} element={children} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );

  return Wrapper;
};

describe('VariablesTab', () => {
  it('should display view full value button and not display edit button if instance is completed or canceled', async () => {
    mockFetchProcessDefinitionXml().withSuccess('');
    mockFetchProcessInstance().withSuccess({
      processInstanceKey: '1',
      state: 'TERMINATED',
      startDate: '2018-06-21',
      endDate: null,
      processDefinitionKey: '2',
      processDefinitionVersion: 1,
      processDefinitionVersionTag: null,
      processDefinitionId: 'someKey',
      tenantId: '<default>',
      processDefinitionName: 'someProcessName',
      hasIncident: false,
      parentProcessInstanceKey: null,
      parentElementInstanceKey: null,
      rootProcessInstanceKey: null,
      tags: [],
      businessId: null,
    });
    mockSearchVariables().withSuccess({
      items: [createVariable()],
      page: {
        totalItems: 1,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });

    render(<VariablesTab />, {wrapper: getWrapper()});

    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });

    expect(
      screen.queryByRole('button', {name: /edit variable/i}),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole('button', {
        name: /Open/i,
      }),
    ).toBeInTheDocument();
  });
});

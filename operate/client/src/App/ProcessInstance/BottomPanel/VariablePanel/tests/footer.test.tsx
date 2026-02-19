/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {mockProcessInstance} from './mocks';
import {mockProcessWithInputOutputMappingsXML} from 'modules/testUtils';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockSearchVariables} from 'modules/mocks/api/v2/variables/searchVariables';
import {mockVariables} from './index.setup';
import {VariablePanel} from '../index';
import {mockSearchJobs} from 'modules/mocks/api/v2/jobs/searchJobs';
import {mockFetchElementInstance} from 'modules/mocks/api/v2/elementInstances/fetchElementInstance';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {useEffect} from 'react';
import {modificationsStore} from 'modules/stores/modifications';
import {Paths} from 'modules/Routes';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';

const TestSelectionControls: React.FC = () => {
  const {selectElementInstance} = useProcessInstanceElementSelection();
  return (
    <button
      type="button"
      onClick={() =>
        selectElementInstance({
          elementId: 'neverFails',
          elementInstanceKey: '3',
        })
      }
    >
      select completed element
    </button>
  );
};

const getWrapper = (
  initialEntries: React.ComponentProps<
    typeof MemoryRouter
  >['initialEntries'] = [Paths.processInstance('1')],
) => {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        modificationsStore.reset();
      };
    }, []);

    return (
      <ProcessDefinitionKeyContext.Provider value="123">
        <QueryClientProvider client={getMockQueryClient()}>
          <MemoryRouter initialEntries={initialEntries}>
            <Routes>
              <Route
                path={Paths.processInstance()}
                element={
                  <>
                    <TestSelectionControls />
                    {children}
                  </>
                }
              />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>
      </ProcessDefinitionKeyContext.Provider>
    );
  };
  return Wrapper;
};

describe('Footer', () => {
  beforeEach(() => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithInputOutputMappingsXML,
    );
    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: [
        {
          elementId: 'start',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 1,
        },
        {
          elementId: 'neverFails',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 1,
        },
      ],
    });
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
  });

  it('should hide/disable add variable button if add/edit variable button is clicked', async () => {
    mockSearchVariables().withSuccess(mockVariables);
    mockSearchVariables().withSuccess(mockVariables);

    const {user} = render(
      <VariablePanel setListenerTabVisibility={vi.fn()} />,
      {wrapper: getWrapper()},
    );
    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });

    await waitFor(() => {
      expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled();
    });

    await user.click(screen.getByRole('button', {name: /add variable/i}));
    await waitFor(() =>
      expect(
        screen.queryByRole('button', {name: /add variable/i}),
      ).not.toBeInTheDocument(),
    );

    await user.click(screen.getByRole('button', {name: /exit edit mode/i}));
    expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled();

    const [firstEditVariableButton] = screen.getAllByRole('button', {
      name: /edit variable/i,
    });
    expect(firstEditVariableButton).toBeInTheDocument();
    await user.click(firstEditVariableButton!);
    expect(screen.getByRole('button', {name: /add variable/i})).toBeDisabled();

    await user.click(screen.getByRole('button', {name: /exit edit mode/i}));
    expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled();
  });

  it('should disable add variable button when selected element is not running', async () => {
    const mockSearchVariablesPayload = {
      items: [],
      page: {
        totalItems: 0,
      },
    };
    mockSearchVariables().withSuccess(mockSearchVariablesPayload);
    mockSearchVariables().withSuccess(mockSearchVariablesPayload);
    mockSearchVariables().withSuccess(mockSearchVariablesPayload);
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});

    mockFetchElementInstance('2').withSuccess({
      elementInstanceKey: '2',
      elementId: 'start',
      elementName: 'Start',
      type: 'START_EVENT',
      state: 'ACTIVE',
      startDate: '2018-06-21',
      processDefinitionId: 'someKey',
      processInstanceKey: '1',
      processDefinitionKey: '2',
      hasIncident: false,
      tenantId: '<default>',
    });

    const {user} = render(
      <VariablePanel setListenerTabVisibility={vi.fn()} />,
      {
        wrapper: getWrapper([
          `${Paths.processInstance('1')}?elementId=start&elementInstanceKey=2`,
        ]),
      },
    );

    await waitFor(() =>
      expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled(),
    );

    mockSearchVariables().withSuccess(mockSearchVariablesPayload);
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});

    mockFetchElementInstance('3').withSuccess({
      elementInstanceKey: '3',
      elementId: 'neverFails',
      elementName: 'Never Fails',
      type: 'SERVICE_TASK',
      state: 'COMPLETED',
      startDate: '2018-06-21',
      endDate: '2018-06-21',
      processDefinitionId: 'someKey',
      processInstanceKey: '1',
      processDefinitionKey: '2',
      hasIncident: false,
      tenantId: '<default>',
    });

    await user.click(
      screen.getByRole('button', {name: /select completed element/i}),
    );

    await waitFor(() =>
      expect(
        screen.getByRole('button', {name: /add variable/i}),
      ).toBeDisabled(),
    );
  });

  it('should disable add variable button when loading', async () => {
    mockSearchVariables().withSuccess(mockVariables);
    mockSearchVariables().withSuccess(mockVariables);

    render(<VariablePanel setListenerTabVisibility={vi.fn()} />, {
      wrapper: getWrapper(),
    });

    await waitFor(() =>
      expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled(),
    );
  });

  it('should disable add variable button if instance state is cancelled', async () => {
    mockFetchProcessInstance().withSuccess({
      ...mockProcessInstance,
      state: 'TERMINATED',
    });
    mockSearchVariables().withSuccess(mockVariables);
    mockSearchVariables().withSuccess(mockVariables);

    render(<VariablePanel setListenerTabVisibility={vi.fn()} />, {
      wrapper: getWrapper(),
    });

    await waitFor(() =>
      expect(
        screen.getByRole('button', {name: /add variable/i}),
      ).toBeDisabled(),
    );
  });
});

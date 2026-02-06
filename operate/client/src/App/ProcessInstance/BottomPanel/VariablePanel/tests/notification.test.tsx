/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {VariablePanel} from '../index';
import {render, screen, waitFor} from 'modules/testing-library';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {
  createInstance,
  createvariable,
  mockProcessWithInputOutputMappingsXML,
} from 'modules/testUtils';
import {modificationsStore} from 'modules/stores/modifications';
import {mockFetchFlowNodeMetadata} from 'modules/mocks/api/processInstances/fetchFlowNodeMetaData';
import {singleInstanceMetadata} from 'modules/mocks/metadata';
import {useEffect} from 'react';
import {Paths} from 'modules/Routes';
import {notificationsStore} from 'modules/stores/notifications';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {init} from 'modules/utils/flowNodeMetadata';
import {type ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockFetchProcessInstance as mockFetchProcessInstanceDeprecated} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockSearchVariables} from 'modules/mocks/api/v2/variables/searchVariables';
import {mockUpdateElementInstanceVariables} from 'modules/mocks/api/v2/elementInstances/updateElementInstanceVariables';
import {mockSearchJobs} from 'modules/mocks/api/v2/jobs/searchJobs';

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

const getWrapper = (
  initialEntries: React.ComponentProps<
    typeof MemoryRouter
  >['initialEntries'] = [Paths.processInstance('1')],
) => {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        flowNodeSelectionStore.reset();
        flowNodeMetaDataStore.reset();
        modificationsStore.reset();
        processInstanceDetailsStore.reset();
      };
    }, []);

    return (
      <ProcessDefinitionKeyContext.Provider value="123">
        <QueryClientProvider client={getMockQueryClient()}>
          <MemoryRouter initialEntries={initialEntries}>
            <Routes>
              <Route path={Paths.processInstance()} element={children} />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>
      </ProcessDefinitionKeyContext.Provider>
    );
  };
  return Wrapper;
};

describe.skip('VariablePanel', () => {
  beforeEach(() => {
    const mockProcessInstance: ProcessInstance = {
      processInstanceKey: 'instance_id',
      state: 'ACTIVE',
      startDate: '2018-06-21',
      processDefinitionKey: '2',
      processDefinitionVersion: 1,
      processDefinitionId: 'someKey',
      tenantId: '<default>',
      processDefinitionName: 'someProcessName',
      hasIncident: false,
    };

    const mockProcessInstanceDeprecated = createInstance();

    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessInstanceDeprecated().withSuccess(
      mockProcessInstanceDeprecated,
    );
    mockFetchProcessInstanceDeprecated().withSuccess(
      mockProcessInstanceDeprecated,
    );
    mockFetchProcessInstanceDeprecated().withSuccess(
      mockProcessInstanceDeprecated,
    );

    const statistics = [
      {
        elementId: 'TEST_FLOW_NODE',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        elementId: 'Activity_0qtp1k6',
        active: 0,
        canceled: 0,
        incidents: 1,
        completed: 0,
      },
    ];

    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: statistics,
    });
    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: statistics,
    });
    mockSearchVariables().withSuccess({
      items: [createvariable()],
      page: {
        totalItems: 1,
      },
    });
    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);
    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithInputOutputMappingsXML,
    );
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});

    init('process-instance', statistics);
    flowNodeSelectionStore.init();
    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: 'instance_id',
        state: 'ACTIVE',
      }),
    );
  });

  afterEach(() => {
    vi.clearAllTimers();
  });

  it('should display error notification if add variable operation could not be created', async () => {
    const {user} = render(
      <VariablePanel setListenerTabVisibility={vi.fn()} />,
      {wrapper: getWrapper()},
    );
    await waitFor(() =>
      expect(
        screen.getByRole('button', {
          name: /add variable/i,
        }),
      ).toBeEnabled(),
    );

    await user.click(
      screen.getByRole('button', {
        name: /add variable/i,
      }),
    );
    expect(
      screen.queryByRole('button', {
        name: /add variable/i,
      }),
    ).not.toBeInTheDocument();

    await user.type(
      screen.getByRole('textbox', {
        name: /name/i,
      }),
      'foo',
    );
    await user.type(
      screen.getByRole('textbox', {
        name: /value/i,
      }),
      '"bar"',
    );

    await waitFor(() =>
      expect(
        screen.getByRole('button', {
          name: /save variable/i,
        }),
      ).toBeEnabled(),
    );

    mockUpdateElementInstanceVariables('instance_id').withDelayedServerError();
    mockSearchVariables().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });

    await user.click(
      screen.getByRole('button', {
        name: /save variable/i,
      }),
    );

    expect(
      await screen.findByRole('button', {
        name: /add variable/i,
      }),
    ).toBeInTheDocument();

    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      isDismissable: true,
      kind: 'error',
      subtitle: 'Internal Server Error',
      title: 'Variable could not be saved',
    });
  });

  it('should display warning notification if add variable operation could not be created because of authorization error', async () => {
    const statistics = [
      {
        elementId: 'TEST_FLOW_NODE',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        elementId: 'Activity_0qtp1k6',
        active: 0,
        canceled: 0,
        incidents: 1,
        completed: 0,
      },
    ];

    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: statistics,
    });

    const {user} = render(
      <VariablePanel setListenerTabVisibility={vi.fn()} />,
      {wrapper: getWrapper()},
    );
    await waitFor(() =>
      expect(
        screen.getByRole('button', {
          name: /add variable/i,
        }),
      ).toBeEnabled(),
    );

    await user.click(
      screen.getByRole('button', {
        name: /add variable/i,
      }),
    );
    expect(
      screen.queryByRole('button', {
        name: /add variable/i,
      }),
    ).not.toBeInTheDocument();

    await user.type(
      screen.getByRole('textbox', {
        name: /name/i,
      }),
      'foo',
    );
    await user.type(
      screen.getByRole('textbox', {
        name: /value/i,
      }),
      '"bar"',
    );

    await waitFor(() =>
      expect(
        screen.getByRole('button', {
          name: /save variable/i,
        }),
      ).toBeEnabled(),
    );

    mockUpdateElementInstanceVariables('instance_id').withDelayedServerError(
      403,
    );
    mockSearchVariables().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });

    await user.click(
      screen.getByRole('button', {
        name: /save variable/i,
      }),
    );

    expect(
      await screen.findByRole('button', {
        name: /add variable/i,
      }),
    ).toBeInTheDocument();

    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      isDismissable: true,
      kind: 'warning',
      title: "You don't have permission to perform this operation",
      subtitle: 'Please contact the administrator if you need access.',
    });
  });
});

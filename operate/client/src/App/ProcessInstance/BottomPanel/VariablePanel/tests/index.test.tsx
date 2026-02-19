/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {VariablePanel} from '../index';
import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
  within,
} from 'modules/testing-library';
import {
  createVariable,
  mockProcessWithInputOutputMappingsXML,
} from 'modules/testUtils';
import {act} from 'react';
import {notificationsStore} from 'modules/stores/notifications';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockSearchVariables} from 'modules/mocks/api/v2/variables/searchVariables';
import {mockSearchJobs} from 'modules/mocks/api/v2/jobs/searchJobs';
import {mockUpdateElementInstanceVariables} from 'modules/mocks/api/v2/elementInstances/updateElementInstanceVariables';
import {mockFetchElementInstance} from 'modules/mocks/api/v2/elementInstances/fetchElementInstance';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';
import {getWrapper as getBaseWrapper, mockProcessInstance} from './mocks';

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

const TestSelectionControls: React.FC = () => {
  const {selectElementInstance, selectElement, clearSelection} =
    useProcessInstanceElementSelection();
  return (
    <>
      <button
        type="button"
        onClick={() =>
          selectElementInstance({
            elementId: 'TEST_FLOW_NODE',
            elementInstanceKey: '2',
          })
        }
      >
        select test flow node
      </button>
      <button
        type="button"
        onClick={() =>
          selectElementInstance({
            elementId: 'Activity_0qtp1k6',
            elementInstanceKey: '2',
          })
        }
      >
        select activity
      </button>
      <button
        type="button"
        onClick={() =>
          selectElement({
            elementId: 'Event_0bonl61',
          })
        }
      >
        select end event
      </button>
      <button
        type="button"
        onClick={() =>
          selectElement({
            elementId: 'StartEvent_1',
          })
        }
      >
        select start event
      </button>
      <button type="button" onClick={() => clearSelection()}>
        clear selection
      </button>
    </>
  );
};

const getWrapper = (...args: Parameters<typeof getBaseWrapper>) => {
  const BaseWrapper = getBaseWrapper(...args);

  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => (
    <BaseWrapper>
      <>
        <TestSelectionControls />
        {children}
      </>
    </BaseWrapper>
  );

  return Wrapper;
};

describe('VariablePanel', () => {
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

  beforeEach(() => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessInstance().withSuccess(mockProcessInstance);

    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: statistics,
    });
    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: statistics,
    });
    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: statistics,
    });

    mockSearchVariables().withSuccess({
      items: [createVariable()],
      page: {
        totalItems: 1,
      },
    });
    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithInputOutputMappingsXML,
    );
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
  });

  afterEach(() => {
    vi.clearAllTimers();
  });

  it('should render variables', async () => {
    mockSearchVariables().withSuccess({
      items: [createVariable()],
      page: {
        totalItems: 1,
      },
    });

    render(<VariablePanel setListenerTabVisibility={vi.fn()} />, {
      wrapper: getWrapper(),
    });

    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();
  });

  it('should add new variable', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});
    mockUpdateElementInstanceVariables(
      `:${mockProcessInstance.processInstanceKey}`,
    ).withDelay(null);

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

    mockSearchVariables().withSuccess({
      items: [
        createVariable(),
        createVariable({
          variableKey: '2251799813725337-foo',
          name: 'foo',
          value: '"bar"',
        }),
      ],
      page: {totalItems: 2},
    });
    mockSearchVariables().withSuccess({
      items: [
        createVariable(),
        createVariable({
          variableKey: '2251799813725337-foo',
          name: 'foo',
          value: '"bar"',
        }),
      ],
      page: {totalItems: 2},
    });

    await user.click(
      screen.getByRole('button', {
        name: /save variable/i,
      }),
    );
    expect(
      screen.queryByRole('button', {
        name: /add variable/i,
      }),
    ).not.toBeInTheDocument();

    expect(screen.getByTestId('full-variable-loader')).toBeInTheDocument();

    const withinVariablesList = within(screen.getByTestId('variables-list'));
    expect(
      withinVariablesList.queryByTestId('variable-foo'),
    ).not.toBeInTheDocument();

    await waitForElementToBeRemoved(
      screen.queryByTestId('full-variable-loader'),
    );

    expect(
      screen.getByRole('button', {
        name: /add variable/i,
      }),
    ).toBeInTheDocument();

    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      isDismissable: true,
      kind: 'success',
      title: 'Variable added',
    });

    expect(
      await withinVariablesList.findByTestId('variable-foo'),
    ).toBeInTheDocument();

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should remove pending variable if scope changes', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

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

    mockSearchVariables().withSuccess({items: [], page: {totalItems: 0}});
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
    mockUpdateElementInstanceVariables(
      `:${mockProcessInstance.processInstanceKey}`,
    ).withSuccess(null);

    await act(async () => {
      await vi.runOnlyPendingTimersAsync();
    });

    await waitFor(() =>
      expect(
        screen.getByRole('button', {
          name: /save variable/i,
        }),
      ).toBeEnabled(),
    );
    expect(
      screen.queryByRole('button', {
        name: /add variable/i,
      }),
    ).not.toBeInTheDocument();

    mockSearchVariables().withSuccess({items: [], page: {totalItems: 0}});
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});

    mockFetchElementInstance('2').withSuccess({
      elementInstanceKey: '2',
      elementId: 'TEST_FLOW_NODE',
      elementName: 'Test Flow Node',
      type: 'SERVICE_TASK',
      state: 'ACTIVE',
      startDate: '2018-06-21',
      processDefinitionId: 'someKey',
      processInstanceKey: mockProcessInstance.processInstanceKey,
      processDefinitionKey: '2',
      hasIncident: false,
      tenantId: '<default>',
    });

    await user.click(
      screen.getByRole('button', {name: /select test flow node/i}),
    );

    expect(
      screen.getByRole('button', {
        name: /add variable/i,
      }),
    ).toBeInTheDocument();
    expect(screen.queryByDisplayValue('foo')).not.toBeInTheDocument();
    expect(screen.queryByDisplayValue('"bar"')).not.toBeInTheDocument();

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should display validation error if backend validation fails while adding variable', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    mockSearchVariables().withSuccess({
      items: [createVariable()],
      page: {totalItems: 1},
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

    mockUpdateElementInstanceVariables(
      `:${mockProcessInstance.processInstanceKey}`,
    ).withServerError(400);

    await waitFor(() =>
      expect(
        screen.getByRole('button', {
          name: /save variable/i,
        }),
      ).toBeEnabled(),
    );
    await user.click(
      screen.getByRole('button', {
        name: /save variable/i,
      }),
    );

    await waitFor(() =>
      expect(
        screen.queryByTestId('full-variable-loader'),
      ).not.toBeInTheDocument(),
    );

    expect(notificationsStore.displayNotification).toHaveBeenCalledWith(
      expect.objectContaining({
        kind: 'error',
        title: 'Variable could not be saved',
      }),
    );
  });

  it('should select correct tab when navigating between flow nodes', async () => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockSearchVariables().withSuccess({
      items: [createVariable()],
      page: {totalItems: 1},
    });

    const {user} = render(
      <VariablePanel setListenerTabVisibility={vi.fn()} />,
      {wrapper: getWrapper()},
    );
    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();

    mockSearchVariables().withSuccess({
      items: [createVariable({name: 'test2'})],
      page: {totalItems: 1},
    });
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithInputOutputMappingsXML,
    );

    mockFetchElementInstance('2').withSuccess({
      elementInstanceKey: '2',
      elementId: 'Activity_0qtp1k6',
      elementName: 'Activity',
      type: 'SERVICE_TASK',
      state: 'ACTIVE',
      startDate: '2018-06-21',
      processDefinitionId: 'someKey',
      processInstanceKey: mockProcessInstance.processInstanceKey,
      processDefinitionKey: '2',
      hasIncident: true,
      tenantId: '<default>',
    });

    await user.click(screen.getByRole('button', {name: /select activity/i}));

    expect(await screen.findByText('test2')).toBeInTheDocument();

    await user.click(screen.getByRole('tab', {name: 'Input Mappings'}));

    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
    mockSearchVariables().withSuccess({
      items: [createVariable({name: 'test2'})],
      page: {totalItems: 1},
    });
    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithInputOutputMappingsXML,
    );

    mockSearchElementInstances().withSuccess({
      items: [
        {
          elementInstanceKey: '10',
          elementId: 'Event_0bonl61',
          elementName: 'End Event',
          type: 'END_EVENT',
          state: 'COMPLETED',
          startDate: '2018-06-21',
          processDefinitionId: 'someKey',
          processInstanceKey: mockProcessInstance.processInstanceKey,
          processDefinitionKey: '2',
          hasIncident: false,
          tenantId: '<default>',
        },
      ],
      page: {totalItems: 1},
    });

    await user.click(screen.getByRole('button', {name: /select end event/i}));

    expect(
      await screen.findByText('No Input Mappings defined'),
    ).toBeInTheDocument();

    mockSearchVariables().withSuccess({
      items: [createVariable({name: 'test2'})],
      page: {totalItems: 1},
    });
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});

    await user.click(screen.getByRole('button', {name: /clear selection/i}));

    await waitFor(() =>
      expect(
        screen.queryByText('No Input Mappings defined'),
      ).not.toBeInTheDocument(),
    );
    expect(screen.getByRole('tab', {name: 'Variables'})).toBeInTheDocument();
    expect(
      screen.queryByRole('tab', {name: 'Input Mappings'}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('tab', {name: 'Output Mappings'}),
    ).not.toBeInTheDocument();

    mockSearchVariables().withSuccess({items: [], page: {totalItems: 0}});
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});

    mockSearchElementInstances().withSuccess({
      items: [
        {
          elementInstanceKey: '20',
          elementId: 'StartEvent_1',
          elementName: 'Start Event',
          type: 'START_EVENT',
          state: 'COMPLETED',
          startDate: '2018-06-21',
          processDefinitionId: 'someKey',
          processInstanceKey: mockProcessInstance.processInstanceKey,
          processDefinitionKey: '2',
          hasIncident: false,
          tenantId: '<default>',
        },
      ],
      page: {totalItems: 1},
    });

    await user.click(screen.getByRole('button', {name: /select start event/i}));

    expect(
      await screen.findByText('No Input Mappings defined'),
    ).toBeInTheDocument();

    expect(
      screen.queryByRole('heading', {name: 'Variables'}),
    ).not.toBeInTheDocument();

    expect(screen.getByRole('tab', {name: 'Variables'})).toBeInTheDocument();
    expect(
      screen.getByRole('tab', {name: 'Input Mappings'}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('tab', {name: 'Output Mappings'}),
    ).toBeInTheDocument();
  });
});

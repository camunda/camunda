/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {VariablesTab} from '../index';
import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
  within,
} from 'modules/testing-library';
import {createVariable} from 'modules/testUtils';
import {act} from 'react';
import {notificationsStore} from 'modules/stores/notifications';
import {mockFetchElementInstancesStatistics} from 'modules/mocks/api/v2/elementInstances/elementInstancesStatistics/fetchElementInstancesStatistics';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockSearchVariables} from 'modules/mocks/api/v2/variables/searchVariables';
import {mockSearchJobs} from 'modules/mocks/api/v2/jobs/searchJobs';
import {mockUpdateElementInstanceVariables} from 'modules/mocks/api/v2/elementInstances/updateElementInstanceVariables';
import {mockFetchElementInstance} from 'modules/mocks/api/v2/elementInstances/fetchElementInstance';

import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';
import {getWrapper as getBaseWrapper, mockProcessInstance} from './mocks';

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

const TestSelectionControls: React.FC = () => {
  const {selectElementInstance} = useProcessInstanceElementSelection();
  return (
    <>
      <button
        type="button"
        onClick={() =>
          selectElementInstance({
            elementId: 'TEST_ELEMENT',
            elementInstanceKey: '2',
          })
        }
      >
        select test element
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

describe('VariablesTab', () => {
  const statistics = [
    {
      elementId: 'TEST_ELEMENT',
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

    mockFetchElementInstancesStatistics().withSuccess({
      items: statistics,
    });
    mockFetchElementInstancesStatistics().withSuccess({
      items: statistics,
    });
    mockFetchElementInstancesStatistics().withSuccess({
      items: statistics,
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
    mockFetchProcessDefinitionXml().withSuccess('');
    mockSearchJobs().withSuccess({
      items: [],
      page: {
        totalItems: 0,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });
  });

  afterEach(() => {
    vi.clearAllTimers();
  });

  it('should render variables', async () => {
    mockSearchVariables().withSuccess({
      items: [createVariable()],
      page: {
        totalItems: 1,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });

    render(<VariablesTab />, {
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

    const {user} = render(<VariablesTab />, {wrapper: getWrapper()});
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
          name: /save/i,
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
      page: {
        totalItems: 2,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
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
      page: {
        totalItems: 2,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });

    await user.click(
      screen.getByRole('button', {
        name: /save/i,
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

    const {user} = render(<VariablesTab />, {wrapper: getWrapper()});
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

    mockSearchVariables().withSuccess({
      items: [],
      page: {
        totalItems: 0,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });
    mockSearchJobs().withSuccess({
      items: [],
      page: {
        totalItems: 0,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });
    mockUpdateElementInstanceVariables(
      `:${mockProcessInstance.processInstanceKey}`,
    ).withSuccess(null);

    await act(async () => {
      await vi.runOnlyPendingTimersAsync();
    });

    await waitFor(() =>
      expect(
        screen.getByRole('button', {
          name: /save/i,
        }),
      ).toBeEnabled(),
    );
    expect(
      screen.queryByRole('button', {
        name: /add variable/i,
      }),
    ).not.toBeInTheDocument();

    mockSearchVariables().withSuccess({
      items: [],
      page: {
        totalItems: 0,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });
    mockSearchJobs().withSuccess({
      items: [],
      page: {
        totalItems: 0,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });

    mockFetchElementInstance('2').withSuccess({
      elementInstanceKey: '2',
      elementId: 'TEST_ELEMENT',
      elementName: 'Test Element',
      type: 'SERVICE_TASK',
      state: 'ACTIVE',
      startDate: '2018-06-21',
      endDate: null,
      processDefinitionId: 'someKey',
      processInstanceKey: mockProcessInstance.processInstanceKey,
      processDefinitionKey: '2',
      rootProcessInstanceKey: null,
      hasIncident: false,
      incidentKey: null,
      tenantId: '<default>',
    });

    await user.click(
      screen.getByRole('button', {name: /select test element/i}),
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
      page: {
        totalItems: 1,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });

    const {user} = render(<VariablesTab />, {wrapper: getWrapper()});
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
          name: /save/i,
        }),
      ).toBeEnabled(),
    );
    await user.click(
      screen.getByRole('button', {
        name: /save/i,
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
});

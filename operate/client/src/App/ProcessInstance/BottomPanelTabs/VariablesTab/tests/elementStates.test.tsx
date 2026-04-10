/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {VariablesTab} from '../index';
import {render, screen, waitFor} from 'modules/testing-library';
import {
  createVariable,
  mockProcessWithInputOutputMappingsXML,
} from 'modules/testUtils';
import {modificationsStore} from 'modules/stores/modifications';
import {mockFetchElementInstancesStatistics} from 'modules/mocks/api/v2/elementInstances/elementInstancesStatistics/fetchElementInstancesStatistics';
import {getWrapper as getBaseWrapper, mockProcessInstance} from './mocks';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {cancelAllTokens} from 'modules/utils/modifications';
import {mockSearchVariables} from 'modules/mocks/api/v2/variables/searchVariables';
import {mockSearchJobs} from 'modules/mocks/api/v2/jobs/searchJobs';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';
import {mockFetchElementInstance} from 'modules/mocks/api/v2/elementInstances/fetchElementInstance';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';
import {act} from 'react';
import type {ElementInstance} from '@camunda/camunda-api-zod-schemas/8.10';

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

const defaultStatistics = [
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

const finishedTokenStatistics = [
  {
    elementId: 'StartEvent_1',
    active: 1,
    canceled: 0,
    incidents: 0,
    completed: 0,
  },
  {
    elementId: 'Activity_0qtp1k6',
    active: 0,
    canceled: 0,
    incidents: 0,
    completed: 1,
  },
];

const activityElementInstance: ElementInstance = {
  elementInstanceKey: '2251799813695856',
  elementId: 'Activity_0qtp1k6',
  elementName: 'Activity',
  type: 'SERVICE_TASK',
  state: 'ACTIVE',
  startDate: '2018-06-21',
  endDate: null,
  processDefinitionId: 'someKey',
  processInstanceKey: '1',
  processDefinitionKey: '2',
  rootProcessInstanceKey: null,
  hasIncident: false,
  incidentKey: null,
  tenantId: '<default>',
};

const emptyVariablesResponse = {
  items: [],
  page: {
    totalItems: 0,
    startCursor: null,
    endCursor: null,
    hasMoreTotalItems: false,
  },
};

const emptyJobsResponse = {
  items: [],
  page: {
    totalItems: 0,
    startCursor: null,
    endCursor: null,
    hasMoreTotalItems: false,
  },
};

const setupElementStateMocks = (statistics = defaultStatistics) => {
  mockFetchProcessInstance().withSuccess(mockProcessInstance);
  mockFetchProcessDefinitionXml().withSuccess(
    mockProcessWithInputOutputMappingsXML,
  );
  mockFetchElementInstancesStatistics().withSuccess({
    items: statistics,
  });
  mockSearchJobs().withSuccess(emptyJobsResponse);
  mockSearchJobs().withSuccess(emptyJobsResponse);
};

const TestSelectionControls: React.FC = () => {
  const {selectElement, selectElementInstance} =
    useProcessInstanceElementSelection();

  return (
    <>
      <button
        type="button"
        onClick={() =>
          selectElement({
            elementId: 'Activity_0qtp1k6',
          })
        }
      >
        select activity element
      </button>
      <button
        type="button"
        onClick={() =>
          selectElementInstance({
            elementId: 'Activity_0qtp1k6',
            elementInstanceKey: '2251799813695856',
          })
        }
      >
        select existing activity scope
      </button>
      <button
        type="button"
        onClick={() =>
          selectElementInstance({
            elementId: 'Activity_0qtp1k6',
            elementInstanceKey: 'some-new-scope-id',
            isPlaceholder: true,
          })
        }
      >
        select new activity scope
      </button>
      <button
        type="button"
        onClick={() =>
          selectElement({
            elementId: 'element-without-running-tokens',
          })
        }
      >
        select no-token element
      </button>
      <button
        type="button"
        onClick={() =>
          selectElementInstance({
            elementId: 'element-without-running-tokens',
            elementInstanceKey: 'some-new-scope-id-1',
            isPlaceholder: true,
          })
        }
      >
        select no-token new scope
      </button>
      <button
        type="button"
        onClick={() =>
          selectElementInstance({
            elementId: 'another-element-without-any-tokens',
            elementInstanceKey: 'some-new-parent-scope-id',
            isPlaceholder: true,
          })
        }
      >
        select no-token parent scope
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

describe('VariablesTab element states', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should display correct state for element that has only one running token on it', async () => {
    setupElementStateMocks();
    mockSearchVariables().withSuccess({
      items: [createVariable()],
      page: {
        totalItems: 1,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });

    modificationsStore.enableModificationMode();

    const {user} = render(<VariablesTab />, {
      wrapper: getWrapper(),
    });

    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();

    mockSearchElementInstances().withSuccess({
      items: [activityElementInstance],
      page: {
        totalItems: 1,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });
    mockSearchVariables().withSuccess(emptyVariablesResponse);
    mockSearchVariables().withSuccess(emptyVariablesResponse);
    mockSearchJobs().withSuccess(emptyJobsResponse);

    await user.click(
      screen.getByRole('button', {name: /^select activity element$/i}),
    );

    act(() => {
      cancelAllTokens('Activity_0qtp1k6', 1, 1, {});
    });

    await waitFor(() => {
      expect(screen.queryByText('testVariableName')).not.toBeInTheDocument();
    });
    expect(
      screen.queryByRole('button', {name: /add variable/i}),
    ).not.toBeInTheDocument();

    act(() => {
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'ADD_TOKEN',
          element: {
            id: 'Activity_0qtp1k6',
            name: 'Element with running tokens',
          },
          affectedTokenCount: 1,
          visibleAffectedTokenCount: 1,
          scopeId: 'some-new-scope-id',
          parentScopeIds: {},
        },
      });
    });

    expect(
      await screen.findByText(
        'To view the variables, select a single element instance in the instance history.',
      ),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /add variable/i}),
    ).not.toBeInTheDocument();

    act(() => {
      modificationsStore.removeElementModification({
        operation: 'CANCEL_TOKEN',
        element: {
          id: 'Activity_0qtp1k6',
          name: 'Element with running tokens',
        },
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
      });
    });

    expect(
      await screen.findByText(
        'To view the variables, select a single element instance in the instance history.',
      ),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /add variable/i}),
    ).not.toBeInTheDocument();

    mockFetchElementInstance('2251799813695856').withSuccess(
      activityElementInstance,
    );
    mockSearchVariables().withSuccess(emptyVariablesResponse);
    mockSearchJobs().withSuccess(emptyJobsResponse);

    await user.click(
      screen.getByRole('button', {name: /^select existing activity scope$/i}),
    );

    expect(
      screen.queryByText(
        'To view the variables, select a single element instance in the instance history.',
      ),
    ).not.toBeInTheDocument();
    expect(
      await screen.findByText('The element has no variables'),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /add variable/i}),
    ).not.toBeInTheDocument();

    await user.click(
      screen.getByRole('button', {name: /^select new activity scope$/i}),
    );

    expect(
      screen.queryByText(
        'To view the variables, select a single element instance in the instance history.',
      ),
    ).not.toBeInTheDocument();
    expect(
      await screen.findByText('The element has no variables'),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /add variable/i}),
    ).toBeInTheDocument();
  });

  it('should display correct state for element that has no running or finished tokens on it', async () => {
    setupElementStateMocks();
    mockSearchVariables().withSuccess(emptyVariablesResponse);
    mockSearchVariables().withSuccess({
      items: [createVariable()],
      page: {
        totalItems: 1,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });

    modificationsStore.enableModificationMode();

    const {user} = render(<VariablesTab />, {wrapper: getWrapper()});
    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();

    mockSearchElementInstances().withSuccess({
      items: [],
      page: {
        totalItems: 0,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });
    mockSearchVariables().withSuccess(emptyVariablesResponse);
    mockSearchJobs().withSuccess(emptyJobsResponse);

    await user.click(
      screen.getByRole('button', {name: /^select no-token element$/i}),
    );

    expect(
      screen.queryByRole('button', {name: /add variable/i}),
    ).not.toBeInTheDocument();
    expect(screen.queryByText('testVariableName')).not.toBeInTheDocument();
    expect(
      screen.getByText('The element has no variables'),
    ).toBeInTheDocument();

    act(() => {
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'ADD_TOKEN',
          element: {
            id: 'element-without-running-tokens',
            name: 'Element without running tokens',
          },
          affectedTokenCount: 1,
          visibleAffectedTokenCount: 1,
          scopeId: 'some-new-scope-id',
          parentScopeIds: {
            'another-element-without-any-tokens': 'some-new-parent-scope-id',
          },
        },
      });
    });

    expect(
      await screen.findByText('The element has no variables'),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /add variable/i}),
    ).toBeInTheDocument();

    act(() => {
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'ADD_TOKEN',
          element: {
            id: 'element-without-running-tokens',
            name: 'Element without running tokens',
          },
          affectedTokenCount: 1,
          visibleAffectedTokenCount: 1,
          scopeId: 'some-new-scope-id-2',
          parentScopeIds: {},
        },
      });
    });

    expect(
      await screen.findByText(
        'To view the variables, select a single element instance in the instance history.',
      ),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /add variable/i}),
    ).not.toBeInTheDocument();

    mockSearchVariables().withSuccess(emptyVariablesResponse);
    mockSearchJobs().withSuccess(emptyJobsResponse);

    await user.click(
      screen.getByRole('button', {name: /^select no-token new scope$/i}),
    );

    expect(
      await screen.findByText('The element has no variables'),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /add variable/i}),
    ).toBeInTheDocument();

    mockSearchVariables().withSuccess(emptyVariablesResponse);
    mockSearchJobs().withSuccess(emptyJobsResponse);

    await user.click(
      screen.getByRole('button', {name: /^select no-token parent scope$/i}),
    );

    expect(
      await screen.findByText('The element has no variables'),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /add variable/i}),
    ).toBeInTheDocument();
  });

  it('should display correct state for element that has only one finished token on it', async () => {
    setupElementStateMocks(finishedTokenStatistics);
    mockSearchVariables().withSuccess(emptyVariablesResponse);
    mockSearchVariables().withSuccess({
      items: [createVariable()],
      page: {
        totalItems: 1,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });

    modificationsStore.enableModificationMode();

    const {user} = render(<VariablesTab />, {
      wrapper: getWrapper(),
    });

    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();

    mockSearchElementInstances().withSuccess({
      items: [
        {
          ...activityElementInstance,
          state: 'COMPLETED',
        },
      ],
      page: {
        totalItems: 1,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });
    mockSearchVariables().withSuccess(emptyVariablesResponse);
    mockSearchJobs().withSuccess(emptyJobsResponse);

    await user.click(
      screen.getByRole('button', {name: /^select activity element$/i}),
    );

    await waitFor(() => {
      expect(
        screen.getByText(/the element has no variables/i),
      ).toBeInTheDocument();
    });
    expect(
      screen.queryByRole('button', {name: /add variable/i}),
    ).not.toBeInTheDocument();

    act(() => {
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'ADD_TOKEN',
          element: {
            id: 'Activity_0qtp1k6',
            name: 'Element with finished tokens',
          },
          affectedTokenCount: 1,
          visibleAffectedTokenCount: 1,
          scopeId: 'some-new-scope-id',
          parentScopeIds: {},
        },
      });
    });

    expect(
      await screen.findByText(
        /to view the variables, select a single element instance in the instance history/i,
      ),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /add variable/i}),
    ).not.toBeInTheDocument();

    mockSearchVariables().withSuccess(emptyVariablesResponse);
    mockSearchJobs().withSuccess(emptyJobsResponse);

    await user.click(
      screen.getByRole('button', {name: /^select new activity scope$/i}),
    );

    await waitFor(() => {
      expect(screen.queryByTestId('variables-spinner')).not.toBeInTheDocument();
      expect(
        screen.getByText(/the element has no variables/i),
      ).toBeInTheDocument();
    });
    expect(
      screen.getByRole('button', {name: /add variable/i}),
    ).toBeInTheDocument();
  });
});

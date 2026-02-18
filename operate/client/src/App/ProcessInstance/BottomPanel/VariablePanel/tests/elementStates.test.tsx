/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {VariablePanel} from '../index';
import {render, screen, waitFor} from 'modules/testing-library';
import {
  createVariable,
  mockProcessWithInputOutputMappingsXML,
} from 'modules/testUtils';
import {modificationsStore} from 'modules/stores/modifications';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
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
import type {ElementInstance} from '@camunda/camunda-api-zod-schemas/8.8';

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

const defaultStatistics = [
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
  processDefinitionId: 'someKey',
  processInstanceKey: '1',
  processDefinitionKey: '2',
  hasIncident: false,
  tenantId: '<default>',
};

const emptyVariablesResponse = {
  items: [],
  page: {totalItems: 0},
};

const emptyJobsResponse = {
  items: [],
  page: {totalItems: 0},
};

const setupElementStateMocks = (statistics = defaultStatistics) => {
  mockFetchProcessInstance().withSuccess(mockProcessInstance);
  mockFetchProcessDefinitionXml().withSuccess(
    mockProcessWithInputOutputMappingsXML,
  );
  mockFetchFlownodeInstancesStatistics().withSuccess({
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
        select activity flow node
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
            elementId: 'flowNode-without-running-tokens',
          })
        }
      >
        select no-token flow node
      </button>
      <button
        type="button"
        onClick={() =>
          selectElementInstance({
            elementId: 'flowNode-without-running-tokens',
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
            elementId: 'another-flownode-without-any-tokens',
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

describe('VariablePanel element states', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should display correct state for element that has only one running token on it', async () => {
    setupElementStateMocks();
    mockSearchVariables().withSuccess({
      items: [createVariable()],
      page: {totalItems: 1},
    });

    modificationsStore.enableModificationMode();

    const {user} = render(
      <VariablePanel setListenerTabVisibility={vi.fn()} />,
      {
        wrapper: getWrapper(),
      },
    );

    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();

    mockSearchElementInstances().withSuccess({
      items: [activityElementInstance],
      page: {totalItems: 1},
    });
    mockSearchVariables().withSuccess(emptyVariablesResponse);
    mockSearchVariables().withSuccess(emptyVariablesResponse);
    mockSearchJobs().withSuccess(emptyJobsResponse);

    await user.click(
      screen.getByRole('button', {name: /^select activity flow node$/i}),
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
          flowNode: {
            id: 'Activity_0qtp1k6',
            name: 'Flow Node with running tokens',
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
        'To view the Variables, select a single Flow Node Instance in the Instance History.',
      ),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /add variable/i}),
    ).not.toBeInTheDocument();

    act(() => {
      modificationsStore.removeFlowNodeModification({
        operation: 'CANCEL_TOKEN',
        flowNode: {
          id: 'Activity_0qtp1k6',
          name: 'Flow Node with running tokens',
        },
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
      });
    });

    expect(
      await screen.findByText(
        'To view the Variables, select a single Flow Node Instance in the Instance History.',
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
        'To view the Variables, select a single Flow Node Instance in the Instance History.',
      ),
    ).not.toBeInTheDocument();
    expect(
      await screen.findByText('The Flow Node has no Variables'),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /add variable/i}),
    ).not.toBeInTheDocument();

    await user.click(
      screen.getByRole('button', {name: /^select new activity scope$/i}),
    );

    expect(
      screen.queryByText(
        'To view the Variables, select a single Flow Node Instance in the Instance History.',
      ),
    ).not.toBeInTheDocument();
    expect(
      await screen.findByText('The Flow Node has no Variables'),
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
      page: {totalItems: 1},
    });

    modificationsStore.enableModificationMode();

    const {user} = render(
      <VariablePanel setListenerTabVisibility={vi.fn()} />,
      {wrapper: getWrapper()},
    );
    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();

    mockSearchElementInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });
    mockSearchVariables().withSuccess(emptyVariablesResponse);
    mockSearchJobs().withSuccess(emptyJobsResponse);

    await user.click(
      screen.getByRole('button', {name: /^select no-token flow node$/i}),
    );

    expect(
      screen.queryByRole('button', {name: /add variable/i}),
    ).not.toBeInTheDocument();
    expect(screen.queryByText('testVariableName')).not.toBeInTheDocument();
    expect(
      screen.getByText('The Flow Node has no Variables'),
    ).toBeInTheDocument();

    act(() => {
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'ADD_TOKEN',
          flowNode: {
            id: 'flowNode-without-running-tokens',
            name: 'Flow Node without running tokens',
          },
          affectedTokenCount: 1,
          visibleAffectedTokenCount: 1,
          scopeId: 'some-new-scope-id',
          parentScopeIds: {
            'another-flownode-without-any-tokens': 'some-new-parent-scope-id',
          },
        },
      });
    });

    expect(
      await screen.findByText('The Flow Node has no Variables'),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /add variable/i}),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('tab', {name: 'Input Mappings'}));
    expect(screen.getByText('No Input Mappings defined')).toBeInTheDocument();

    await user.click(screen.getByRole('tab', {name: 'Variables'}));
    expect(
      await screen.findByText('The Flow Node has no Variables'),
    ).toBeInTheDocument();
    expect(screen.queryByTestId('variables-spinner')).not.toBeInTheDocument();

    act(() => {
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'ADD_TOKEN',
          flowNode: {
            id: 'flowNode-without-running-tokens',
            name: 'Flow Node without running tokens',
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
        'To view the Variables, select a single Flow Node Instance in the Instance History.',
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
      await screen.findByText('The Flow Node has no Variables'),
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
      await screen.findByText('The Flow Node has no Variables'),
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
      page: {totalItems: 1},
    });

    modificationsStore.enableModificationMode();

    const {user} = render(
      <VariablePanel setListenerTabVisibility={vi.fn()} />,
      {wrapper: getWrapper()},
    );

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
      page: {totalItems: 1},
    });
    mockSearchVariables().withSuccess(emptyVariablesResponse);
    mockSearchJobs().withSuccess(emptyJobsResponse);

    await user.click(
      screen.getByRole('button', {name: /^select activity flow node$/i}),
    );

    await waitFor(() =>
      expect(screen.queryByTestId('variables-spinner')).not.toBeInTheDocument(),
    );
    expect(
      await screen.findByText(/the flow node has no variables/i),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /add variable/i}),
    ).not.toBeInTheDocument();

    act(() => {
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'ADD_TOKEN',
          flowNode: {
            id: 'Activity_0qtp1k6',
            name: 'Flow Node with finished tokens',
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
        /to view the variables, select a single flow node instance in the instance history/i,
      ),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /add variable/i}),
    ).not.toBeInTheDocument();

    await user.click(screen.getByRole('tab', {name: 'Variables'}));
    await waitFor(() =>
      expect(screen.queryByTestId('variables-spinner')).not.toBeInTheDocument(),
    );
    expect(
      await screen.findByText(
        /to view the variables, select a single flow node instance in the instance history/i,
      ),
    ).toBeInTheDocument();

    mockSearchVariables().withSuccess(emptyVariablesResponse);
    mockSearchJobs().withSuccess(emptyJobsResponse);

    await user.click(
      screen.getByRole('button', {name: /^select new activity scope$/i}),
    );

    await waitFor(() =>
      expect(screen.queryByTestId('variables-spinner')).not.toBeInTheDocument(),
    );
    expect(
      await screen.findByText(/the flow node has no variables/i),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /add variable/i}),
    ).toBeInTheDocument();
  });
});

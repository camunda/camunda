/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor, within} from 'modules/testing-library';
import {testData} from './index.setup';
import {ProcessInstance} from '../index';
import {storeStateLocally} from 'modules/utils/localStorage';
import {incidentsStore} from 'modules/stores/incidents';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import * as flowNodeInstanceUtils from 'modules/utils/flowNodeInstance';
import {mockFetchProcessInstance as mockFetchProcessInstanceDeprecated} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {singleInstanceMetadata} from 'modules/mocks/metadata';
import {mockFetchFlowNodeMetadata} from 'modules/mocks/api/processInstances/fetchFlowNodeMetaData';
import {PAGE_TITLE} from 'modules/constants';
import {getProcessName} from 'modules/utils/instance';
import {getWrapper, mockRequests, waitForPollingsToBeComplete} from './mocks';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockSearchVariables} from 'modules/mocks/api/v2/variables/searchVariables';
import {createvariable} from 'modules/testUtils';
import {act} from 'react';

vi.mock('modules/utils/bpmn');

const clearPollingStates = () => {
  incidentsStore.isPollRequestRunning = false;
  flowNodeInstanceStore.isPollRequestRunning = false;
};

const triggerVisibilityChange = (visibility: 'hidden' | 'visible') => {
  Object.defineProperty(document, 'visibilityState', {
    value: visibility,
    configurable: true,
  });

  document.dispatchEvent(new Event('visibilitychange'));
};

describe('ProcessInstance', () => {
  beforeEach(() => {
    mockRequests();
  });

  it('should render and set the page title', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});
    mockSearchVariables().withSuccess({
      items: [createvariable()],
      page: {
        totalItems: 1,
      },
    });

    render(<ProcessInstance />, {wrapper: getWrapper()});
    expect(screen.queryByTestId('variables-skeleton')).not.toBeInTheDocument();
    expect(await screen.findByTestId('diagram')).toBeInTheDocument();
    expect(screen.getByTestId('diagram-body')).toBeInTheDocument();
    expect(screen.getByText('Instance History')).toBeInTheDocument();
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();
    expect(
      within(screen.getByTestId('instance-header')).getByTestId(
        'INCIDENT-icon',
      ),
    ).toBeInTheDocument();

    expect(document.title).toBe(
      PAGE_TITLE.INSTANCE(
        testData.fetch.onPageLoad.processInstance.id,
        getProcessName(testData.fetch.onPageLoad.processInstance),
      ),
    );

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should display skeletons until instance is available', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    mockFetchProcessInstanceDeprecated().withServerError(404);

    render(<ProcessInstance />, {wrapper: getWrapper()});

    mockFetchProcessInstanceDeprecated().withSuccess(
      testData.fetch.onPageLoad.processInstance,
    );

    vi.runOnlyPendingTimers();

    expect(screen.queryByTestId('diagram-spinner')).not.toBeInTheDocument();
    expect(
      screen.queryByTestId('instance-history-skeleton'),
    ).not.toBeInTheDocument();
    expect(screen.queryByTestId('variables-skeleton')).not.toBeInTheDocument();

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should not trigger polling for variables when scope id changed', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);

    const {user} = render(<ProcessInstance />, {
      wrapper: getWrapper({
        selectableFlowNode: {
          flowNodeId: 'taskD',
          flowNodeInstanceId: 'test-id',
        },
      }),
    });

    storeStateLocally({
      [`hideModificationHelperModal`]: true,
    });

    clearPollingStates();

    mockRequests();
    vi.runOnlyPendingTimers();

    expect(
      await screen.findByRole('button', {
        name: /modify instance/i,
      }),
    ).toBeInTheDocument();

    await user.click(
      screen.getByRole('button', {
        name: /modify instance/i,
      }),
    );

    clearPollingStates();

    mockRequests();
    vi.runOnlyPendingTimers();

    clearPollingStates();
    vi.runOnlyPendingTimers();

    await user.click(screen.getByRole('button', {name: 'Select flow node'}));

    clearPollingStates();

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should display forbidden content', async () => {
    mockFetchProcessInstanceDeprecated().withServerError(403);
    mockFetchProcessInstance().withServerError(403);

    render(<ProcessInstance />, {wrapper: getWrapper()});

    expect(
      await screen.findByText(
        '403 - You do not have permission to view this information',
      ),
    ).toBeInTheDocument();

    expect(
      screen.getByText('Contact your administrator to get access.'),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('link', {name: 'Learn more about permissions'}),
    ).toHaveAttribute(
      'href',
      'https://docs.camunda.io/docs/self-managed/operate-deployment/operate-authentication/#resource-based-permissions',
    );
  });

  it('should display forbidden content after polling', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});
    mockFetchProcessInstance().withServerError(403);

    render(<ProcessInstance />, {wrapper: getWrapper()});

    vi.runOnlyPendingTimers();

    expect(
      await screen.findByText(
        '403 - You do not have permission to view this information',
      ),
    ).toBeInTheDocument();

    expect(
      screen.getByText('Contact your administrator to get access.'),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('link', {name: 'Learn more about permissions'}),
    ).toHaveAttribute(
      'href',
      'https://docs.camunda.io/docs/self-managed/operate-deployment/operate-authentication/#resource-based-permissions',
    );

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should stop polling if document is not visible', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    const handlePollingIncidentsSpy = vi.spyOn(incidentsStore, 'handlePolling');

    const initFlowNodeInstanceSpy = vi.spyOn(flowNodeInstanceUtils, 'init');
    const startPollingFlowNodeInstanceSpy = vi.spyOn(
      flowNodeInstanceUtils,
      'startPolling',
    );

    mockRequests();
    render(<ProcessInstance />, {wrapper: getWrapper()});

    expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(0);
    expect(initFlowNodeInstanceSpy).toHaveBeenCalledTimes(0);

    clearPollingStates();
    act(() => {
      vi.runOnlyPendingTimers();
    });
    await waitFor(() =>
      expect(initFlowNodeInstanceSpy).toHaveBeenCalledTimes(1),
    );
    act(() => {
      vi.runOnlyPendingTimers();
    });
    await waitFor(() =>
      expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(1),
    );

    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });

    triggerVisibilityChange('hidden');

    clearPollingStates();
    mockRequests();

    vi.runOnlyPendingTimers();

    expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(1);

    clearPollingStates();
    mockRequests();

    vi.runOnlyPendingTimers();

    expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(1);
    expect(startPollingFlowNodeInstanceSpy).toHaveBeenCalledTimes(0);

    mockRequests();

    triggerVisibilityChange('visible');

    clearPollingStates();
    mockRequests();

    vi.runOnlyPendingTimers();

    await waitFor(() => {
      expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(3);
    });

    await waitFor(() => {
      expect(startPollingFlowNodeInstanceSpy).toHaveBeenCalledTimes(1);
    });

    await waitForPollingsToBeComplete();

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should not start polling in first render if document is not visible', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});
    triggerVisibilityChange('hidden');

    const handlePollingIncidentsSpy = vi.spyOn(incidentsStore, 'handlePolling');

    const initFlowNodeInstanceSpy = vi.spyOn(flowNodeInstanceUtils, 'init');
    const startPollingFlowNodeInstanceSpy = vi.spyOn(
      flowNodeInstanceUtils,
      'startPolling',
    );

    render(<ProcessInstance />, {wrapper: getWrapper()});

    mockRequests();

    expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(0);
    expect(initFlowNodeInstanceSpy).toHaveBeenCalledTimes(0);

    clearPollingStates();
    await act(async () => {
      await vi.runOnlyPendingTimersAsync();
    });

    expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(0);
    expect(initFlowNodeInstanceSpy).toHaveBeenCalledTimes(1);

    triggerVisibilityChange('visible');
    clearPollingStates();

    expect(startPollingFlowNodeInstanceSpy).toHaveBeenCalledTimes(1);
    await act(async () => {
      await vi.runOnlyPendingTimersAsync();
    });

    await waitFor(() => {
      expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(2);
    });

    mockRequests();
    await act(async () => {
      await vi.runOnlyPendingTimersAsync();
    });

    await waitFor(() => {
      expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(3);
    });
    expect(startPollingFlowNodeInstanceSpy).toHaveBeenCalledTimes(1);

    await waitForPollingsToBeComplete();

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should not restart polling when document is visible for finished instances', async () => {
    mockFetchProcessInstanceDeprecated().withSuccess(
      testData.fetch.onPageLoad.completedProcessInstance,
    );

    vi.useFakeTimers({shouldAdvanceTime: true});

    const handlePollingIncidentsSpy = vi.spyOn(incidentsStore, 'handlePolling');

    const handlePollingFlowNodeInstanceSpy = vi.spyOn(
      flowNodeInstanceStore,
      'pollInstances',
    );

    render(<ProcessInstance />, {wrapper: getWrapper()});

    mockRequests();

    expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(0);
    expect(handlePollingFlowNodeInstanceSpy).toHaveBeenCalledTimes(0);

    triggerVisibilityChange('hidden');
    triggerVisibilityChange('visible');

    clearPollingStates();

    vi.runOnlyPendingTimers();

    expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(0);
    expect(handlePollingFlowNodeInstanceSpy).toHaveBeenCalledTimes(0);

    vi.clearAllTimers();
    vi.useRealTimers();
  });
});

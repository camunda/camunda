/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor, within} from 'modules/testing-library';
import {testData} from '../index.setup';
import {ProcessInstance} from './index';
import {storeStateLocally} from 'modules/utils/localStorage';
import {variablesStore} from 'modules/stores/variables';
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

jest.mock('modules/utils/bpmn');

const clearPollingStates = () => {
  variablesStore.isPollRequestRunning = false;
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

  afterEach(() => {
    window.clientConfig = undefined;
  });

  it('should render and set the page title', async () => {
    jest.useFakeTimers();

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

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should display skeletons until instance is available', async () => {
    jest.useFakeTimers();

    mockFetchProcessInstanceDeprecated().withServerError(404);

    render(<ProcessInstance />, {wrapper: getWrapper()});

    mockFetchProcessInstanceDeprecated().withSuccess(
      testData.fetch.onPageLoad.processInstance,
    );

    jest.runOnlyPendingTimers();

    await waitFor(() => {
      expect(screen.queryByTestId('diagram-spinner')).not.toBeInTheDocument();
      expect(
        screen.queryByTestId('instance-history-skeleton'),
      ).not.toBeInTheDocument();
      expect(
        screen.queryByTestId('variables-skeleton'),
      ).not.toBeInTheDocument();
    });

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should not trigger polling for variables when scope id changed', async () => {
    jest.useFakeTimers();

    const handlePollingVariablesSpy = jest.spyOn(
      variablesStore,
      'handlePolling',
    );

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

    expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(0);

    clearPollingStates();

    mockRequests();
    jest.runOnlyPendingTimers();

    await waitFor(() =>
      expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(1),
    );

    await user.click(
      screen.getByRole('button', {
        name: /modify instance/i,
      }),
    );

    clearPollingStates();

    mockRequests();
    jest.runOnlyPendingTimers();

    expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(1);

    clearPollingStates();
    jest.runOnlyPendingTimers();

    expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(1);

    await user.click(screen.getByRole('button', {name: 'Select flow node'}));

    clearPollingStates();

    mockRequests();
    jest.runOnlyPendingTimers();

    await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));

    expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(1);

    jest.clearAllTimers();
    jest.useRealTimers();
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
    jest.useFakeTimers();
    render(<ProcessInstance />, {wrapper: getWrapper()});

    mockFetchProcessInstance().withServerError(403);

    jest.runOnlyPendingTimers();

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

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should stop polling if document is not visible', async () => {
    jest.useFakeTimers();

    const handlePollingVariablesSpy = jest.spyOn(
      variablesStore,
      'handlePolling',
    );

    const handlePollingIncidentsSpy = jest.spyOn(
      incidentsStore,
      'handlePolling',
    );

    const initFlowNodeInstanceSpy = jest.spyOn(flowNodeInstanceUtils, 'init');
    const startPollingFlowNodeInstanceSpy = jest.spyOn(
      flowNodeInstanceUtils,
      'startPolling',
    );

    render(<ProcessInstance />, {wrapper: getWrapper()});

    mockRequests();

    expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(0);
    expect(initFlowNodeInstanceSpy).toHaveBeenCalledTimes(0);
    expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(0);

    clearPollingStates();
    jest.runOnlyPendingTimers();
    await waitFor(() =>
      expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(1),
    );
    await waitFor(() =>
      expect(initFlowNodeInstanceSpy).toHaveBeenCalledTimes(1),
    );
    await waitFor(() =>
      expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(1),
    );

    await waitFor(() => {
      expect(variablesStore.state.status).toBe('fetched');
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });

    triggerVisibilityChange('hidden');

    clearPollingStates();
    mockRequests();

    jest.runOnlyPendingTimers();

    expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(1);
    expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(1);

    clearPollingStates();
    mockRequests();

    jest.runOnlyPendingTimers();

    expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(1);
    expect(startPollingFlowNodeInstanceSpy).toHaveBeenCalledTimes(0);
    expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(1);

    mockRequests();

    triggerVisibilityChange('visible');

    clearPollingStates();
    mockRequests();

    jest.runOnlyPendingTimers();

    await waitFor(() => {
      expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(4);
      expect(startPollingFlowNodeInstanceSpy).toHaveBeenCalledTimes(1);
      expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(4);
    });

    await waitForPollingsToBeComplete();

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should not start polling in first render if document is not visible', async () => {
    jest.useFakeTimers();
    triggerVisibilityChange('hidden');

    const handlePollingVariablesSpy = jest.spyOn(
      variablesStore,
      'handlePolling',
    );

    const handlePollingIncidentsSpy = jest.spyOn(
      incidentsStore,
      'handlePolling',
    );

    const initFlowNodeInstanceSpy = jest.spyOn(flowNodeInstanceUtils, 'init');
    const startPollingFlowNodeInstanceSpy = jest.spyOn(
      flowNodeInstanceUtils,
      'startPolling',
    );

    render(<ProcessInstance />, {wrapper: getWrapper()});

    mockRequests();

    expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(0);
    expect(initFlowNodeInstanceSpy).toHaveBeenCalledTimes(0);
    expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(0);

    clearPollingStates();
    jest.runOnlyPendingTimers();

    expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(0);
    expect(initFlowNodeInstanceSpy).toHaveBeenCalledTimes(0);
    expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(0);

    triggerVisibilityChange('visible');

    clearPollingStates();
    await waitFor(() =>
      expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(1),
    );
    expect(startPollingFlowNodeInstanceSpy).toHaveBeenCalledTimes(1);
    expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(1);

    mockRequests();
    jest.runOnlyPendingTimers();

    await waitFor(() =>
      expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(2),
    );
    await waitFor(() =>
      expect(startPollingFlowNodeInstanceSpy).toHaveBeenCalledTimes(1),
    );
    await waitFor(() =>
      expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(2),
    );

    await waitForPollingsToBeComplete();

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should not restart polling when document is visible for finished instances', async () => {
    mockFetchProcessInstanceDeprecated().withSuccess(
      testData.fetch.onPageLoad.completedProcessInstance,
    );

    jest.useFakeTimers();

    const handlePollingVariablesSpy = jest.spyOn(
      variablesStore,
      'handlePolling',
    );

    const handlePollingIncidentsSpy = jest.spyOn(
      incidentsStore,
      'handlePolling',
    );

    const handlePollingFlowNodeInstanceSpy = jest.spyOn(
      flowNodeInstanceStore,
      'pollInstances',
    );

    render(<ProcessInstance />, {wrapper: getWrapper()});

    mockRequests();

    expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(0);
    expect(handlePollingFlowNodeInstanceSpy).toHaveBeenCalledTimes(0);
    expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(0);

    triggerVisibilityChange('hidden');
    triggerVisibilityChange('visible');

    clearPollingStates();

    jest.runOnlyPendingTimers();

    expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(0);
    expect(handlePollingFlowNodeInstanceSpy).toHaveBeenCalledTimes(0);
    expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(0);

    jest.clearAllTimers();
    jest.useRealTimers();
  });
});

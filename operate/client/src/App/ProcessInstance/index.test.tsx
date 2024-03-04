/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {
  render,
  waitForElementToBeRemoved,
  screen,
  waitFor,
  within,
} from 'modules/testing-library';
import {testData} from './index.setup';
import {ProcessInstance} from './index';
import {storeStateLocally} from 'modules/utils/localStorage';
import {variablesStore} from 'modules/stores/variables';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {sequenceFlowsStore} from 'modules/stores/sequenceFlows';
import {incidentsStore} from 'modules/stores/incidents';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {processInstanceDetailsStatisticsStore} from 'modules/stores/processInstanceDetailsStatistics';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {Paths} from 'modules/Routes';
import {singleInstanceMetadata} from 'modules/mocks/metadata';
import {mockFetchFlowNodeMetadata} from 'modules/mocks/api/processInstances/fetchFlowNodeMetaData';
import {PAGE_TITLE} from 'modules/constants';
import {getProcessName} from 'modules/utils/instance';
import {notificationsStore} from 'modules/stores/notifications';
import {getWrapper, mockRequests, waitForPollingsToBeComplete} from './mocks';

const handleRefetchSpy = jest.spyOn(
  processInstanceDetailsStore,
  'handleRefetch',
);

jest.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: jest.fn(() => () => {}),
  },
}));

jest.mock('modules/utils/bpmn');

const clearPollingStates = () => {
  variablesStore.isPollRequestRunning = false;
  sequenceFlowsStore.isPollRequestRunning = false;
  processInstanceDetailsStore.isPollRequestRunning = false;
  incidentsStore.isPollRequestRunning = false;
  flowNodeInstanceStore.isPollRequestRunning = false;
  processInstanceDetailsStatisticsStore.isPollRequestRunning = false;
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
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton'),
    );
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

    mockFetchProcessInstance().withServerError(404);

    render(<ProcessInstance />, {wrapper: getWrapper()});

    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();
    expect(screen.getByTestId('diagram-spinner')).toBeInTheDocument();
    expect(screen.getByTestId('instance-history-skeleton')).toBeInTheDocument();
    expect(screen.getByTestId('variables-skeleton')).toBeInTheDocument();

    mockFetchProcessInstance().withSuccess(
      testData.fetch.onPageLoad.processInstance,
    );

    jest.runOnlyPendingTimers();
    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();
    expect(screen.getByTestId('diagram-spinner')).toBeInTheDocument();
    expect(screen.getByTestId('instance-history-skeleton')).toBeInTheDocument();
    expect(screen.getByTestId('variables-skeleton')).toBeInTheDocument();
    expect(screen.getByTestId('diagram-spinner')).toBeInTheDocument();
    expect(screen.getByTestId('instance-history-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('instance-header-skeleton'),
    );

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

  it('should poll 3 times for not found instance, then redirect to instances page and display notification', async () => {
    jest.useFakeTimers();

    mockFetchProcessInstance().withServerError(404);

    render(<ProcessInstance />, {
      wrapper: getWrapper({initialPath: Paths.processInstance('123')}),
    });

    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();
    expect(screen.getByTestId('diagram-spinner')).toBeInTheDocument();
    expect(screen.getByTestId('instance-history-skeleton')).toBeInTheDocument();
    expect(screen.getByTestId('variables-skeleton')).toBeInTheDocument();

    mockFetchProcessInstance().withServerError(404);
    jest.runOnlyPendingTimers();
    await waitFor(() =>
      expect(processInstanceDetailsStore.state.status).toBe('fetching'),
    );

    expect(handleRefetchSpy).toHaveBeenCalledTimes(1);

    mockFetchProcessInstance().withServerError(404);
    jest.runOnlyPendingTimers();
    await waitFor(() => expect(handleRefetchSpy).toHaveBeenCalledTimes(2));

    mockFetchProcessInstance().withServerError(404);
    jest.runOnlyPendingTimers();
    await waitFor(() => expect(handleRefetchSpy).toHaveBeenCalledTimes(3));

    await waitFor(() => {
      expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/processes$/);
      expect(screen.getByTestId('search')).toHaveTextContent(
        /^\?active=true&incidents=true$/,
      );
    });

    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      kind: 'error',
      title: 'Instance 123 could not be found',
      isDismissable: true,
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
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton'),
    );

    storeStateLocally({
      [`hideModificationHelperModal`]: true,
    });

    expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(0);

    clearPollingStates();

    mockRequests();
    jest.runOnlyPendingTimers();

    expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(1);

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

    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton'),
    );
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

    mockRequests();
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
    const handlePollingSequenceFlowsSpy = jest.spyOn(
      sequenceFlowsStore,
      'handlePolling',
    );

    const handlePollingInstanceDetailsSpy = jest.spyOn(
      processInstanceDetailsStore,
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

    const handlePollingProcessInstanceDetailStatisticsSpy = jest.spyOn(
      processInstanceDetailsStatisticsStore,
      'handlePolling',
    );

    render(<ProcessInstance />, {wrapper: getWrapper()});
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton'),
    );

    mockRequests();

    expect(handlePollingSequenceFlowsSpy).toHaveBeenCalledTimes(0);
    expect(handlePollingInstanceDetailsSpy).toHaveBeenCalledTimes(0);
    expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(0);
    expect(handlePollingFlowNodeInstanceSpy).toHaveBeenCalledTimes(0);
    expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(0);
    expect(
      handlePollingProcessInstanceDetailStatisticsSpy,
    ).toHaveBeenCalledTimes(0);

    clearPollingStates();
    jest.runOnlyPendingTimers();
    expect(handlePollingSequenceFlowsSpy).toHaveBeenCalledTimes(1);
    expect(handlePollingInstanceDetailsSpy).toHaveBeenCalledTimes(1);
    expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(1);
    expect(handlePollingFlowNodeInstanceSpy).toHaveBeenCalledTimes(1);
    expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(1);
    expect(
      handlePollingProcessInstanceDetailStatisticsSpy,
    ).toHaveBeenCalledTimes(1);

    await waitFor(() => {
      expect(variablesStore.state.status).toBe('fetched');
      expect(processInstanceDetailsStore.state.status).toBe('fetched');
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });

    triggerVisibilityChange('hidden');

    clearPollingStates();
    mockRequests();

    jest.runOnlyPendingTimers();

    expect(handlePollingSequenceFlowsSpy).toHaveBeenCalledTimes(1);
    expect(handlePollingInstanceDetailsSpy).toHaveBeenCalledTimes(1);
    expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(1);
    expect(handlePollingFlowNodeInstanceSpy).toHaveBeenCalledTimes(1);
    expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(1);
    expect(
      handlePollingProcessInstanceDetailStatisticsSpy,
    ).toHaveBeenCalledTimes(1);

    clearPollingStates();
    mockRequests();

    jest.runOnlyPendingTimers();

    expect(handlePollingSequenceFlowsSpy).toHaveBeenCalledTimes(1);
    expect(handlePollingInstanceDetailsSpy).toHaveBeenCalledTimes(1);
    expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(1);
    expect(handlePollingFlowNodeInstanceSpy).toHaveBeenCalledTimes(1);
    expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(1);
    expect(
      handlePollingProcessInstanceDetailStatisticsSpy,
    ).toHaveBeenCalledTimes(1);

    mockRequests();

    triggerVisibilityChange('visible');

    clearPollingStates();
    mockRequests();

    jest.runOnlyPendingTimers();

    await waitFor(() => {
      expect(handlePollingSequenceFlowsSpy).toHaveBeenCalledTimes(3);
      expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(3);
      expect(handlePollingInstanceDetailsSpy).toHaveBeenCalledTimes(3);
      expect(handlePollingFlowNodeInstanceSpy).toHaveBeenCalledTimes(3);
      expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(3);
      expect(
        handlePollingProcessInstanceDetailStatisticsSpy,
      ).toHaveBeenCalledTimes(3);
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
    const handlePollingSequenceFlowsSpy = jest.spyOn(
      sequenceFlowsStore,
      'handlePolling',
    );

    const handlePollingInstanceDetailsSpy = jest.spyOn(
      processInstanceDetailsStore,
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

    const handlePollingProcessInstanceDetailStatisticsSpy = jest.spyOn(
      processInstanceDetailsStatisticsStore,
      'handlePolling',
    );

    render(<ProcessInstance />, {wrapper: getWrapper()});
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton'),
    );

    mockRequests();

    expect(handlePollingSequenceFlowsSpy).toHaveBeenCalledTimes(0);
    expect(handlePollingInstanceDetailsSpy).toHaveBeenCalledTimes(0);
    expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(0);
    expect(handlePollingFlowNodeInstanceSpy).toHaveBeenCalledTimes(0);
    expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(0);
    expect(
      handlePollingProcessInstanceDetailStatisticsSpy,
    ).toHaveBeenCalledTimes(0);

    clearPollingStates();
    jest.runOnlyPendingTimers();

    expect(handlePollingSequenceFlowsSpy).toHaveBeenCalledTimes(0);
    expect(handlePollingInstanceDetailsSpy).toHaveBeenCalledTimes(0);
    expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(0);
    expect(handlePollingFlowNodeInstanceSpy).toHaveBeenCalledTimes(0);
    expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(0);
    expect(
      handlePollingProcessInstanceDetailStatisticsSpy,
    ).toHaveBeenCalledTimes(0);

    triggerVisibilityChange('visible');

    clearPollingStates();
    expect(handlePollingSequenceFlowsSpy).toHaveBeenCalledTimes(1);
    expect(handlePollingInstanceDetailsSpy).toHaveBeenCalledTimes(1);
    expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(1);
    expect(handlePollingFlowNodeInstanceSpy).toHaveBeenCalledTimes(1);
    expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(1);
    expect(
      handlePollingProcessInstanceDetailStatisticsSpy,
    ).toHaveBeenCalledTimes(1);

    mockRequests();
    jest.runOnlyPendingTimers();

    expect(handlePollingSequenceFlowsSpy).toHaveBeenCalledTimes(2);
    expect(handlePollingInstanceDetailsSpy).toHaveBeenCalledTimes(2);
    expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(2);
    expect(handlePollingFlowNodeInstanceSpy).toHaveBeenCalledTimes(2);
    expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(2);
    expect(
      handlePollingProcessInstanceDetailStatisticsSpy,
    ).toHaveBeenCalledTimes(2);

    await waitForPollingsToBeComplete();

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should not restart polling when document is visible for finished instances', async () => {
    mockFetchProcessInstance().withSuccess(
      testData.fetch.onPageLoad.completedProcessInstance,
    );

    jest.useFakeTimers();

    const handlePollingVariablesSpy = jest.spyOn(
      variablesStore,
      'handlePolling',
    );
    const handlePollingSequenceFlowsSpy = jest.spyOn(
      sequenceFlowsStore,
      'handlePolling',
    );

    const handlePollingInstanceDetailsSpy = jest.spyOn(
      processInstanceDetailsStore,
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

    const handlePollingProcessInstanceDetailStatisticsSpy = jest.spyOn(
      processInstanceDetailsStatisticsStore,
      'handlePolling',
    );

    render(<ProcessInstance />, {wrapper: getWrapper()});
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton'),
    );

    mockRequests();

    expect(handlePollingSequenceFlowsSpy).toHaveBeenCalledTimes(0);
    expect(handlePollingInstanceDetailsSpy).toHaveBeenCalledTimes(0);
    expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(0);
    expect(handlePollingFlowNodeInstanceSpy).toHaveBeenCalledTimes(0);
    expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(0);
    expect(
      handlePollingProcessInstanceDetailStatisticsSpy,
    ).toHaveBeenCalledTimes(0);

    triggerVisibilityChange('hidden');
    triggerVisibilityChange('visible');

    clearPollingStates();

    jest.runOnlyPendingTimers();

    expect(handlePollingSequenceFlowsSpy).toHaveBeenCalledTimes(0);
    expect(handlePollingInstanceDetailsSpy).toHaveBeenCalledTimes(0);
    expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(0);
    expect(handlePollingFlowNodeInstanceSpy).toHaveBeenCalledTimes(0);
    expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(0);
    expect(
      handlePollingProcessInstanceDetailStatisticsSpy,
    ).toHaveBeenCalledTimes(0);

    jest.clearAllTimers();
    jest.useRealTimers();
  });
});

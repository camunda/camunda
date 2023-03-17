/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  Route,
  unstable_HistoryRouter as HistoryRouter,
  Routes,
  Link,
} from 'react-router-dom';
import {
  render,
  waitForElementToBeRemoved,
  screen,
  waitFor,
  within,
} from 'modules/testing-library';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {testData} from './index.setup';
import {mockSequenceFlows} from './TopPanel/index.setup';
import {PAGE_TITLE} from 'modules/constants';
import {getProcessName} from 'modules/utils/instance';
import {ProcessInstance} from './index';
import {
  createBatchOperation,
  createMultiInstanceFlowNodeInstances,
  createVariable,
} from 'modules/testUtils';
import {useNotifications} from 'modules/notifications';
import {LocationLog} from 'modules/utils/LocationLog';
import {modificationsStore} from 'modules/stores/modifications';
import {storeStateLocally} from 'modules/utils/localStorage';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {variablesStore} from 'modules/stores/variables';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {sequenceFlowsStore} from 'modules/stores/sequenceFlows';
import {incidentsStore} from 'modules/stores/incidents';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {processInstanceDetailsStatisticsStore} from 'modules/stores/processInstanceDetailsStatistics';
import {createMemoryHistory} from 'history';
import {mockFetchVariables} from 'modules/mocks/api/processInstances/fetchVariables';
import {mockFetchProcessInstanceDetailStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstanceDetailStatistics';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchProcessInstanceIncidents} from 'modules/mocks/api/processInstances/fetchProcessInstanceIncidents';
import {mockFetchSequenceFlows} from 'modules/mocks/api/processInstances/sequenceFlows';
import {mockFetchFlowNodeInstances} from 'modules/mocks/api/fetchFlowNodeInstances';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {mockFetchFlowNodeMetadata} from 'modules/mocks/api/processInstances/fetchFlowNodeMetaData';
import {mockModify} from 'modules/mocks/api/processInstances/modify';
import {mockIncidents} from 'modules/mocks/incidents';
import {singleInstanceMetadata} from 'modules/mocks/metadata';

const handleRefetchSpy = jest.spyOn(
  processInstanceDetailsStore,
  'handleRefetch'
);

jest.mock('modules/notifications', () => {
  const mockUseNotifications = {
    displayNotification: jest.fn(),
  };

  return {
    useNotifications: () => {
      return mockUseNotifications;
    },
  };
});

jest.mock('modules/utils/bpmn');

type Props = {
  children?: React.ReactNode;
};

const processInstancesMock = createMultiInstanceFlowNodeInstances('4294980768');

const clearPollingStates = () => {
  variablesStore.isPollRequestRunning = false;
  sequenceFlowsStore.isPollRequestRunning = false;
  processInstanceDetailsStore.isPollRequestRunning = false;
  incidentsStore.isPollRequestRunning = false;
  flowNodeInstanceStore.isPollRequestRunning = false;
  processInstanceDetailsStatisticsStore.isPollRequestRunning = false;
};

function getWrapper(
  initialPath: string = '/processes/4294980768',
  contextPath?: string
) {
  const Wrapper: React.FC<Props> = ({children}) => {
    return (
      <ThemeProvider>
        <HistoryRouter
          history={createMemoryHistory({
            initialEntries: [initialPath],
          })}
          basename={contextPath ?? ''}
        >
          <Routes>
            <Route path="/processes/:processInstanceId" element={children} />
            <Route path="/processes" element={<>instances page</>} />
            <Route path="/" element={<>dashboard page</>} />
          </Routes>
          <LocationLog />
        </HistoryRouter>
      </ThemeProvider>
    );
  };

  return Wrapper;
}

const mockRequests = (contextPath: string = '') => {
  mockFetchProcessInstance(contextPath).withSuccess(
    testData.fetch.onPageLoad.processInstanceWithIncident
  );
  mockFetchProcessXML(contextPath).withSuccess('');
  mockFetchSequenceFlows(contextPath).withSuccess(mockSequenceFlows);
  mockFetchFlowNodeInstances(contextPath).withSuccess(
    processInstancesMock.level1
  );
  mockFetchProcessInstanceDetailStatistics(contextPath).withSuccess([
    {
      activityId: 'taskD',
      active: 1,
      incidents: 1,
      completed: 0,
      canceled: 0,
    },
  ]);
  mockFetchVariables(contextPath).withSuccess([createVariable()]);
  mockFetchProcessInstanceIncidents(contextPath).withSuccess({
    ...mockIncidents,
    count: 2,
  });
};

describe('Instance', () => {
  beforeAll(() => {
    //@ts-ignore
    IS_REACT_ACT_ENVIRONMENT = false;
  });

  afterAll(() => {
    //@ts-ignore
    IS_REACT_ACT_ENVIRONMENT = true;
  });

  beforeEach(() => {
    mockRequests();
    modificationsStore.reset();
  });

  afterEach(() => {
    window.clientConfig = undefined;
  });

  it('should render and set the page title', async () => {
    jest.useFakeTimers();

    render(<ProcessInstance />, {wrapper: getWrapper()});
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );
    expect(screen.queryByTestId('skeleton-rows')).not.toBeInTheDocument();
    expect(await screen.findByTestId('diagram')).toBeInTheDocument();
    expect(screen.getByTestId('diagram-panel-body')).toBeInTheDocument();
    expect(screen.getByText('Instance History')).toBeInTheDocument();
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();
    expect(
      within(screen.getByTestId('instance-header')).getByTestId('INCIDENT-icon')
    ).toBeInTheDocument();

    expect(document.title).toBe(
      PAGE_TITLE.INSTANCE(
        testData.fetch.onPageLoad.processInstance.id,
        getProcessName(testData.fetch.onPageLoad.processInstance)
      )
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
    expect(screen.getByTestId('skeleton-rows')).toBeInTheDocument();

    mockFetchProcessInstance().withSuccess(
      testData.fetch.onPageLoad.processInstance
    );

    jest.runOnlyPendingTimers();
    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();
    expect(screen.getByTestId('diagram-spinner')).toBeInTheDocument();
    expect(screen.getByTestId('instance-history-skeleton')).toBeInTheDocument();
    expect(screen.getByTestId('skeleton-rows')).toBeInTheDocument();
    expect(screen.getByTestId('diagram-spinner')).toBeInTheDocument();
    expect(screen.getByTestId('instance-history-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    await waitFor(() => {
      expect(screen.queryByTestId('diagram-spinner')).not.toBeInTheDocument();
      expect(
        screen.queryByTestId('instance-history-skeleton')
      ).not.toBeInTheDocument();
      expect(screen.queryByTestId('skeleton-rows')).not.toBeInTheDocument();
    });

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should poll 3 times for not found instance, then redirect to instances page and display notification', async () => {
    jest.useFakeTimers();

    mockFetchProcessInstance().withServerError(404);

    render(<ProcessInstance />, {wrapper: getWrapper('/processes/123')});

    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();
    expect(screen.getByTestId('diagram-spinner')).toBeInTheDocument();
    expect(screen.getByTestId('instance-history-skeleton')).toBeInTheDocument();
    expect(screen.getByTestId('skeleton-rows')).toBeInTheDocument();

    mockFetchProcessInstance().withServerError(404);
    jest.runOnlyPendingTimers();
    await waitFor(() => expect(handleRefetchSpy).toHaveBeenCalledTimes(1));

    mockFetchProcessInstance().withServerError(404);
    jest.runOnlyPendingTimers();
    await waitFor(() => expect(handleRefetchSpy).toHaveBeenCalledTimes(2));

    mockFetchProcessInstance().withServerError(404);
    jest.runOnlyPendingTimers();
    await waitFor(() => expect(handleRefetchSpy).toHaveBeenCalledTimes(3));

    await waitFor(() => {
      expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/processes$/);
      expect(screen.getByTestId('search')).toHaveTextContent(
        /^\?active=true&incidents=true$/
      );
    });

    expect(useNotifications().displayNotification).toHaveBeenCalledWith(
      'error',
      {
        headline: 'Instance 123 could not be found',
      }
    );

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should display the modifications header and footer when modification mode is enabled', async () => {
    const {user} = render(<ProcessInstance />, {wrapper: getWrapper()});
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    expect(
      screen.queryByText('Process Instance Modification Mode')
    ).not.toBeInTheDocument();
    expect(screen.queryByTestId('discard-all-button')).not.toBeInTheDocument();
    expect(
      screen.queryByTestId('apply-modifications-button')
    ).not.toBeInTheDocument();

    storeStateLocally({
      [`hideModificationHelperModal`]: true,
    });
    await user.click(
      screen.getByRole('button', {
        name: /modify instance/i,
      })
    );

    expect(
      screen.getByText('Process Instance Modification Mode')
    ).toBeInTheDocument();
    expect(screen.getByTestId('discard-all-button')).toBeInTheDocument();
    expect(
      screen.getByTestId('apply-modifications-button')
    ).toBeInTheDocument();

    await user.click(screen.getByTestId('discard-all-button'));
    await user.click(await screen.findByTestId('discard-button'));

    await waitForElementToBeRemoved(() =>
      screen.getByText('Process Instance Modification Mode')
    );

    expect(screen.queryByTestId('discard-all-button')).not.toBeInTheDocument();
    expect(
      screen.queryByTestId('apply-modifications-button')
    ).not.toBeInTheDocument();
  });

  it('should display confirmation modal when discard all is clicked during the modification mode', async () => {
    const {user} = render(<ProcessInstance />, {wrapper: getWrapper()});
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    storeStateLocally({
      [`hideModificationHelperModal`]: true,
    });
    await user.click(
      screen.getByRole('button', {
        name: /modify instance/i,
      })
    );
    await user.click(screen.getByTestId('discard-all-button'));

    expect(
      await screen.findByText(
        /about to discard all added modifications for instance/i
      )
    ).toBeInTheDocument();

    expect(
      screen.getByText(/click "discard" to proceed\./i)
    ).toBeInTheDocument();

    await user.click(screen.getByTestId('cancel-button'));

    await waitForElementToBeRemoved(() =>
      screen.queryByText(
        /About to discard all added modifications for instance/
      )
    );
    expect(
      screen.queryByText(/click "discard" to proceed\./i)
    ).not.toBeInTheDocument();
  });

  it('should display no planned modifications modal when apply modifications is clicked during the modification mode', async () => {
    const {user} = render(<ProcessInstance />, {wrapper: getWrapper()});
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    storeStateLocally({
      [`hideModificationHelperModal`]: true,
    });
    await user.click(
      screen.getByRole('button', {
        name: /modify instance/i,
      })
    );
    await user.click(screen.getByTestId('apply-modifications-button'));

    expect(
      await screen.findByText(/no planned modifications for process instance/i)
    ).toBeInTheDocument();

    expect(
      screen.getByText(/click "ok" to return to the modification mode\./i)
    ).toBeInTheDocument();

    await user.click(screen.getByTestId('ok-button'));

    await waitForElementToBeRemoved(() =>
      screen.queryByText(/no planned modifications for process instance/i)
    );
    expect(
      screen.queryByText(/click "ok" to return to the modification mode\./i)
    ).not.toBeInTheDocument();
  });

  it('should display summary modifications modal when apply modifications is clicked during the modification mode', async () => {
    const {user} = render(<ProcessInstance />, {wrapper: getWrapper()});
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    expect(await screen.findByText('testVariableName')).toBeInTheDocument();
    storeStateLocally({
      [`hideModificationHelperModal`]: true,
    });
    await user.click(
      screen.getByRole('button', {
        name: /modify instance/i,
      })
    );

    mockFetchVariables().withSuccess([]);
    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'taskD',
    });

    mockFetchVariables().withSuccess([createVariable()]);

    await user.click(
      await screen.findByRole('button', {
        name: /add single flow node instance/i,
      })
    );

    mockFetchVariables().withSuccess([createVariable()]);

    await user.click(screen.getByTestId('apply-modifications-button'));

    expect(
      await screen.findByText(/Planned modifications for Process Instance/i)
    ).toBeInTheDocument();
    expect(screen.getByText(/Click "Apply" to proceed./i)).toBeInTheDocument();

    expect(screen.getByText(/flow node modifications/i)).toBeInTheDocument();

    expect(
      screen.getByText('No planned variable modifications')
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Cancel'}));

    await waitForElementToBeRemoved(() =>
      screen.queryByText(/Planned modifications for Process Instance/i)
    );
  });

  it('should stop polling during the modification mode', async () => {
    jest.useFakeTimers();

    const handlePollingVariablesSpy = jest.spyOn(
      variablesStore,
      'handlePolling'
    );
    const handlePollingSequenceFlowsSpy = jest.spyOn(
      sequenceFlowsStore,
      'handlePolling'
    );

    const handlePollingInstanceDetailsSpy = jest.spyOn(
      processInstanceDetailsStore,
      'handlePolling'
    );

    const handlePollingIncidentsSpy = jest.spyOn(
      incidentsStore,
      'handlePolling'
    );

    const handlePollingFlowNodeInstanceSpy = jest.spyOn(
      flowNodeInstanceStore,
      'pollInstances'
    );

    const handlePollingProcessInstanceDetailStatisticsSpy = jest.spyOn(
      processInstanceDetailsStatisticsStore,
      'handlePolling'
    );

    const {user} = render(<ProcessInstance />, {wrapper: getWrapper()});
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    storeStateLocally({
      [`hideModificationHelperModal`]: true,
    });

    mockRequests();

    expect(handlePollingSequenceFlowsSpy).toHaveBeenCalledTimes(0);
    expect(handlePollingInstanceDetailsSpy).toHaveBeenCalledTimes(0);
    expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(0);
    expect(handlePollingFlowNodeInstanceSpy).toHaveBeenCalledTimes(0);
    expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(0);
    expect(
      handlePollingProcessInstanceDetailStatisticsSpy
    ).toHaveBeenCalledTimes(0);

    clearPollingStates();
    jest.runOnlyPendingTimers();
    expect(handlePollingSequenceFlowsSpy).toHaveBeenCalledTimes(1);
    expect(handlePollingInstanceDetailsSpy).toHaveBeenCalledTimes(1);
    expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(1);
    expect(handlePollingFlowNodeInstanceSpy).toHaveBeenCalledTimes(1);
    expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(1);
    expect(
      handlePollingProcessInstanceDetailStatisticsSpy
    ).toHaveBeenCalledTimes(1);

    await waitFor(() => {
      expect(variablesStore.state.status).toBe('fetched');
      expect(processInstanceDetailsStore.state.status).toBe('fetched');
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });

    await user.click(
      screen.getByRole('button', {
        name: /modify instance/i,
      })
    );

    clearPollingStates();
    mockRequests();

    jest.runOnlyPendingTimers();

    expect(handlePollingSequenceFlowsSpy).toHaveBeenCalledTimes(1);
    expect(handlePollingInstanceDetailsSpy).toHaveBeenCalledTimes(1);
    expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(1);
    expect(handlePollingFlowNodeInstanceSpy).toHaveBeenCalledTimes(1);
    expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(1);
    expect(
      handlePollingProcessInstanceDetailStatisticsSpy
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
      handlePollingProcessInstanceDetailStatisticsSpy
    ).toHaveBeenCalledTimes(1);

    await user.click(screen.getByTestId('discard-all-button'));
    await user.click(await screen.findByTestId('discard-button'));

    clearPollingStates();
    mockRequests();

    jest.runOnlyPendingTimers();

    await waitFor(() => {
      expect(handlePollingSequenceFlowsSpy).toHaveBeenCalledTimes(2);
      expect(handlePollingInstanceDetailsSpy).toHaveBeenCalledTimes(2);
      expect(handlePollingFlowNodeInstanceSpy).toHaveBeenCalledTimes(2);
      expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(2);
      expect(
        handlePollingProcessInstanceDetailStatisticsSpy
      ).toHaveBeenCalledTimes(2);
    });

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should display loading overlay when modifications are applied', async () => {
    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);
    mockModify().withSuccess(
      createBatchOperation({type: 'MODIFY_PROCESS_INSTANCE'})
    );

    jest.useFakeTimers();

    const {user} = render(<ProcessInstance />, {wrapper: getWrapper()});
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    storeStateLocally({
      [`hideModificationHelperModal`]: true,
    });
    await user.click(
      screen.getByRole('button', {
        name: /modify instance/i,
      })
    );

    expect(
      screen.getByText('Process Instance Modification Mode')
    ).toBeInTheDocument();

    mockFetchVariables().withSuccess([]);
    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'taskD',
    });

    mockFetchVariables().withSuccess([createVariable()]);

    await user.click(
      await screen.findByRole('button', {
        name: /add single flow node instance/i,
      })
    );

    expect(await screen.findByTestId('badge-plus-icon')).toBeInTheDocument();

    await user.click(screen.getByTestId('apply-modifications-button'));
    await user.click(await screen.findByRole('button', {name: 'Apply'}));
    expect(screen.getByText(/applying modifications.../i)).toBeInTheDocument();

    await waitForElementToBeRemoved(() =>
      screen.getByText(/applying modifications.../i)
    );

    expect(
      screen.queryByText('Process Instance Modification Mode')
    ).not.toBeInTheDocument();

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should not trigger polling for variables when scope id changed', async () => {
    jest.useFakeTimers();

    const handlePollingVariablesSpy = jest.spyOn(
      variablesStore,
      'handlePolling'
    );

    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);

    const {user} = render(<ProcessInstance />, {wrapper: getWrapper()});
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
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
      })
    );

    clearPollingStates();

    mockRequests();
    jest.runOnlyPendingTimers();

    expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(1);

    clearPollingStates();
    jest.runOnlyPendingTimers();

    expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(1);

    flowNodeSelectionStore.setSelection({
      flowNodeId: 'taskD',
      flowNodeInstanceId: 'test-id',
    });

    clearPollingStates();

    mockRequests();
    jest.runOnlyPendingTimers();

    await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));

    expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(1);

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should block navigation when modification mode is enabled', async () => {
    const {user} = render(<ProcessInstance />, {wrapper: getWrapper()});
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    storeStateLocally({
      [`hideModificationHelperModal`]: true,
    });

    await user.click(
      screen.getByRole('button', {
        name: /modify instance/i,
      })
    );

    await user.click(
      screen.getByRole('link', {
        description: /View process someProcessName version 1 instances/,
      })
    );

    expect(
      await screen.findByText(
        'By leaving this page, all planned modification will be discarded.'
      )
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Stay'}));

    await waitForElementToBeRemoved(() =>
      screen.queryByText(
        'By leaving this page, all planned modification will be discarded.'
      )
    );

    await user.click(
      screen.getByRole('link', {
        description: /View process someProcessName version 1 instances/,
      })
    );

    expect(
      await screen.findByText(
        'By leaving this page, all planned modification will be discarded.'
      )
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Leave'}));

    expect(await screen.findByText('instances page')).toBeInTheDocument();
  });

  it('should block navigation when navigating to processes page modification mode is enabled - with context path', async () => {
    const contextPath = '/custom';
    window.clientConfig = {
      contextPath,
    };

    mockRequests(contextPath);

    const {user} = render(<ProcessInstance />, {
      wrapper: getWrapper(`${contextPath}/processes/4294980768`, contextPath),
    });
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    storeStateLocally({
      [`hideModificationHelperModal`]: true,
    });

    await user.click(
      screen.getByRole('button', {
        name: /modify instance/i,
      })
    );

    await user.click(
      screen.getByRole('link', {
        description: /View process someProcessName version 1 instances/,
      })
    );

    expect(
      await screen.findByText(
        'By leaving this page, all planned modification will be discarded.'
      )
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Stay'}));

    await waitForElementToBeRemoved(() =>
      screen.queryByText(
        'By leaving this page, all planned modification will be discarded.'
      )
    );

    await user.click(
      screen.getByRole('link', {
        description: /View process someProcessName version 1 instances/,
      })
    );

    expect(
      await screen.findByText(
        'By leaving this page, all planned modification will be discarded.'
      )
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Leave'}));

    expect(await screen.findByText('instances page')).toBeInTheDocument();
  });

  it('should block navigation when navigating to dashboard with modification mode is enabled - with context path', async () => {
    const contextPath = '/custom';
    window.clientConfig = {
      contextPath,
    };

    mockRequests(contextPath);

    const {user} = render(
      <>
        <Link to="/">go to dashboard</Link>
        <ProcessInstance />
      </>,
      {
        wrapper: getWrapper(`${contextPath}/processes/4294980768`, contextPath),
      }
    );
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    storeStateLocally({
      [`hideModificationHelperModal`]: true,
    });

    await user.click(
      screen.getByRole('button', {
        name: /modify instance/i,
      })
    );

    await user.click(screen.getByText(/go to dashboard/));

    expect(
      await screen.findByText(
        'By leaving this page, all planned modification will be discarded.'
      )
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Stay'}));

    await waitForElementToBeRemoved(() =>
      screen.queryByText(
        'By leaving this page, all planned modification will be discarded.'
      )
    );

    await user.click(screen.getByText(/go to dashboard/));

    expect(
      await screen.findByText(
        'By leaving this page, all planned modification will be discarded.'
      )
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Leave'}));

    expect(await screen.findByText('dashboard page')).toBeInTheDocument();
  });

  it('should display forbidden content', async () => {
    mockFetchProcessInstance().withServerError(403);

    render(<ProcessInstance />, {wrapper: getWrapper()});

    expect(
      await screen.findByText(
        '403 - You do not have permission to view this information'
      )
    ).toBeInTheDocument();

    expect(
      screen.getByText('Contact your administrator to get access.')
    ).toBeInTheDocument();

    expect(
      screen.getByRole('link', {name: 'Learn more about Operate'})
    ).toHaveAttribute(
      'href',
      'https://docs.camunda.io/docs/components/operate/operate-introduction/'
    );
  });

  it('should display forbidden content after polling', async () => {
    jest.useFakeTimers();
    render(<ProcessInstance />, {wrapper: getWrapper()});

    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );
    expect(screen.queryByTestId('skeleton-rows')).not.toBeInTheDocument();
    expect(await screen.findByTestId('diagram')).toBeInTheDocument();
    expect(screen.getByTestId('diagram-panel-body')).toBeInTheDocument();
    expect(screen.getByText('Instance History')).toBeInTheDocument();
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();
    expect(
      within(screen.getByTestId('instance-header')).getByTestId('INCIDENT-icon')
    ).toBeInTheDocument();

    mockRequests();
    mockFetchProcessInstance().withServerError(403);

    jest.runOnlyPendingTimers();

    expect(
      await screen.findByText(
        '403 - You do not have permission to view this information'
      )
    ).toBeInTheDocument();

    expect(
      screen.getByText('Contact your administrator to get access.')
    ).toBeInTheDocument();

    expect(
      screen.getByRole('link', {name: 'Learn more about Operate'})
    ).toHaveAttribute(
      'href',
      'https://docs.camunda.io/docs/components/operate/operate-introduction/'
    );

    jest.clearAllTimers();
    jest.useRealTimers();
  });
});

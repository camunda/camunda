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

import {Link} from 'react-router-dom';
import {
  render,
  waitForElementToBeRemoved,
  screen,
  waitFor,
} from 'modules/testing-library';
import {ProcessInstance} from './index';
import {createBatchOperation, createVariable} from 'modules/testUtils';
import {storeStateLocally} from 'modules/utils/localStorage';
import {variablesStore} from 'modules/stores/variables';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {sequenceFlowsStore} from 'modules/stores/sequenceFlows';
import {incidentsStore} from 'modules/stores/incidents';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {processInstanceDetailsStatisticsStore} from 'modules/stores/processInstanceDetailsStatistics';
import {mockFetchVariables} from 'modules/mocks/api/processInstances/fetchVariables';

import {Paths} from 'modules/Routes';
import {singleInstanceMetadata} from 'modules/mocks/metadata';
import {mockFetchFlowNodeMetadata} from 'modules/mocks/api/processInstances/fetchFlowNodeMetaData';
import {mockModify} from 'modules/mocks/api/processInstances/modify';
import {getWrapper, mockRequests, waitForPollingsToBeComplete} from './mocks';
import {modificationsStore} from 'modules/stores/modifications';

const clearPollingStates = () => {
  variablesStore.isPollRequestRunning = false;
  sequenceFlowsStore.isPollRequestRunning = false;
  processInstanceDetailsStore.isPollRequestRunning = false;
  incidentsStore.isPollRequestRunning = false;
  flowNodeInstanceStore.isPollRequestRunning = false;
  processInstanceDetailsStatisticsStore.isPollRequestRunning = false;
};

jest.mock('modules/utils/bpmn');

describe('ProcessInstance - modification mode', () => {
  beforeEach(() => {
    mockRequests();
    modificationsStore.reset();
  });

  afterEach(() => {
    window.clientConfig = undefined;
  });

  it('should display the modifications header and footer when modification mode is enabled', async () => {
    const {user} = render(<ProcessInstance />, {wrapper: getWrapper()});
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton'),
    );

    expect(
      screen.queryByText('Process Instance Modification Mode'),
    ).not.toBeInTheDocument();
    expect(screen.queryByTestId('discard-all-button')).not.toBeInTheDocument();
    expect(
      screen.queryByTestId('apply-modifications-button'),
    ).not.toBeInTheDocument();

    storeStateLocally({
      [`hideModificationHelperModal`]: true,
    });
    await user.click(
      screen.getByRole('button', {
        name: /modify instance/i,
      }),
    );

    expect(
      screen.getByText('Process Instance Modification Mode'),
    ).toBeInTheDocument();
    expect(screen.getByTestId('discard-all-button')).toBeInTheDocument();
    expect(
      screen.getByTestId('apply-modifications-button'),
    ).toBeInTheDocument();

    mockRequests();

    await user.click(screen.getByTestId('discard-all-button'));
    await user.click(
      await screen.findByRole('button', {name: /danger discard/i}),
    );

    await waitFor(() =>
      expect(
        screen.queryByText('Process Instance Modification Mode'),
      ).not.toBeInTheDocument(),
    );

    expect(screen.queryByTestId('discard-all-button')).not.toBeInTheDocument();
    expect(
      screen.queryByTestId('apply-modifications-button'),
    ).not.toBeInTheDocument();
  });

  it('should display confirmation modal when discard all is clicked during the modification mode', async () => {
    const {user} = render(<ProcessInstance />, {wrapper: getWrapper()});
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton'),
    );

    storeStateLocally({
      [`hideModificationHelperModal`]: true,
    });
    await user.click(
      screen.getByRole('button', {
        name: /modify instance/i,
      }),
    );
    await user.click(screen.getByTestId('discard-all-button'));

    expect(
      await screen.findByText(
        /about to discard all added modifications for instance/i,
      ),
    ).toBeInTheDocument();

    expect(
      screen.getByText(/click "discard" to proceed\./i),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /cancel/i}));

    await waitFor(() =>
      expect(
        screen.queryByText(
          /About to discard all added modifications for instance/,
        ),
      ).not.toBeInTheDocument(),
    );
    expect(
      screen.queryByText(/click "discard" to proceed\./i),
    ).not.toBeInTheDocument();
  });

  it('should disable apply modifications button if there are no modifications pending', async () => {
    const {user} = render(<ProcessInstance />, {wrapper: getWrapper()});
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton'),
    );

    storeStateLocally({
      [`hideModificationHelperModal`]: true,
    });
    await user.click(
      screen.getByRole('button', {
        name: /modify instance/i,
      }),
    );
    expect(screen.getByTestId('apply-modifications-button')).toBeDisabled();
  });

  it('should display summary modifications modal when apply modifications is clicked during the modification mode', async () => {
    const {user} = render(<ProcessInstance />, {
      wrapper: getWrapper({selectableFlowNode: {flowNodeId: 'taskD'}}),
    });
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton'),
    );

    expect(await screen.findByText('testVariableName')).toBeInTheDocument();
    storeStateLocally({
      [`hideModificationHelperModal`]: true,
    });
    await user.click(
      screen.getByRole('button', {
        name: /modify instance/i,
      }),
    );

    mockFetchVariables().withSuccess([]);
    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);

    await user.click(screen.getByRole('button', {name: 'Select flow node'}));

    mockFetchVariables().withSuccess([createVariable()]);

    await user.click(
      await screen.findByRole('button', {
        name: /add single flow node instance/i,
      }),
    );

    mockFetchVariables().withSuccess([createVariable()]);

    await user.click(screen.getByTestId('apply-modifications-button'));

    expect(
      await screen.findByText(/Planned modifications for Process Instance/i),
    ).toBeInTheDocument();
    expect(screen.getByText(/Click "Apply" to proceed./i)).toBeInTheDocument();

    expect(screen.getByText(/flow node modifications/i)).toBeInTheDocument();

    expect(
      screen.getByText('No planned variable modifications'),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Cancel'}));

    await waitFor(() =>
      expect(
        screen.queryByText(/Planned modifications for Process Instance/i),
      ).not.toBeInTheDocument(),
    );
  });

  it('should stop polling during the modification mode', async () => {
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

    const {user} = render(<ProcessInstance />, {wrapper: getWrapper()});
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton'),
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

    await user.click(
      screen.getByRole('button', {
        name: /modify instance/i,
      }),
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
    await user.click(screen.getByTestId('discard-all-button'));
    await user.click(
      await screen.findByRole('button', {name: /danger discard/i}),
    );

    clearPollingStates();
    mockRequests();

    jest.runOnlyPendingTimers();

    await waitFor(() => {
      expect(handlePollingSequenceFlowsSpy).toHaveBeenCalledTimes(3);
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

  it('should display loading overlay when modifications are applied', async () => {
    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);
    mockModify().withSuccess(
      createBatchOperation({type: 'MODIFY_PROCESS_INSTANCE'}),
    );

    jest.useFakeTimers();

    const {user} = render(<ProcessInstance />, {
      wrapper: getWrapper({selectableFlowNode: {flowNodeId: 'taskD'}}),
    });
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton'),
    );

    storeStateLocally({
      [`hideModificationHelperModal`]: true,
    });
    await user.click(
      screen.getByRole('button', {
        name: /modify instance/i,
      }),
    );

    expect(
      screen.getByText('Process Instance Modification Mode'),
    ).toBeInTheDocument();

    mockFetchVariables().withSuccess([]);
    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);

    await user.click(screen.getByRole('button', {name: 'Select flow node'}));

    mockFetchVariables().withSuccess([createVariable()]);

    await user.click(
      await screen.findByRole('button', {
        name: /add single flow node instance/i,
      }),
    );

    expect(await screen.findByTestId('badge-plus-icon')).toBeInTheDocument();

    mockRequests();

    await user.click(screen.getByTestId('apply-modifications-button'));
    await user.click(await screen.findByRole('button', {name: 'Apply'}));
    expect(screen.getByTestId('loading-overlay')).toBeInTheDocument();

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('loading-overlay'),
    );

    expect(
      screen.queryByText('Process Instance Modification Mode'),
    ).not.toBeInTheDocument();

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should block navigation when modification mode is enabled', async () => {
    const {user} = render(<ProcessInstance />, {wrapper: getWrapper()});
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton'),
    );

    storeStateLocally({
      [`hideModificationHelperModal`]: true,
    });

    await user.click(
      screen.getByRole('button', {
        name: /modify instance/i,
      }),
    );

    await user.click(
      screen.getByRole('link', {
        description: /View process "someProcessName version 1" instances/,
      }),
    );

    expect(
      await screen.findByText(
        'By leaving this page, all planned modification/s will be discarded.',
      ),
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Stay'}));

    expect(
      screen.queryByText(
        'By leaving this page, all planned modification/s will be discarded.',
      ),
    ).not.toBeInTheDocument();

    await user.click(
      screen.getByRole('link', {
        description: /View process "someProcessName version 1" instances/,
      }),
    );

    expect(
      await screen.findByText(
        'By leaving this page, all planned modification/s will be discarded.',
      ),
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
      wrapper: getWrapper({
        initialPath: `${contextPath}/processes/4294980768`,
        contextPath,
      }),
    });
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton'),
    );

    storeStateLocally({
      [`hideModificationHelperModal`]: true,
    });

    await user.click(
      screen.getByRole('button', {
        name: /modify instance/i,
      }),
    );

    await user.click(
      screen.getByRole('link', {
        description: /View process "someProcessName version 1" instances/,
      }),
    );

    expect(
      await screen.findByText(
        'By leaving this page, all planned modification/s will be discarded.',
      ),
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Stay'}));

    expect(
      screen.queryByText(
        'By leaving this page, all planned modification/s will be discarded.',
      ),
    ).not.toBeInTheDocument();

    await user.click(
      screen.getByRole('link', {
        description: /View process "someProcessName version 1" instances/,
      }),
    );

    expect(
      await screen.findByText(
        'By leaving this page, all planned modification/s will be discarded.',
      ),
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
        <Link to={Paths.dashboard()}>go to dashboard</Link>
        <ProcessInstance />
      </>,
      {
        wrapper: getWrapper({
          initialPath: `${contextPath}/processes/4294980768`,
          contextPath,
        }),
      },
    );
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton'),
    );

    storeStateLocally({
      [`hideModificationHelperModal`]: true,
    });

    await user.click(
      screen.getByRole('button', {
        name: /modify instance/i,
      }),
    );

    await user.click(screen.getByText(/go to dashboard/));

    expect(
      await screen.findByText(
        'By leaving this page, all planned modification/s will be discarded.',
      ),
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Stay'}));

    expect(
      screen.queryByText(
        'By leaving this page, all planned modification/s will be discarded.',
      ),
    ).not.toBeInTheDocument();

    await user.click(screen.getByText(/go to dashboard/));

    expect(
      await screen.findByText(
        'By leaving this page, all planned modification/s will be discarded.',
      ),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Leave'}));

    expect(await screen.findByText('dashboard page')).toBeInTheDocument();
  });
});

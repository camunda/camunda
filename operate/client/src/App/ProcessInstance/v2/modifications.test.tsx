/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Link} from 'react-router-dom';
import {
  render,
  waitForElementToBeRemoved,
  screen,
  waitFor,
  fireEvent,
} from 'modules/testing-library';
import {ProcessInstance} from './index';
import {createBatchOperation, createVariable} from 'modules/testUtils';
import {storeStateLocally} from 'modules/utils/localStorage';
import {variablesStore} from 'modules/stores/variables';
import {incidentsStore} from 'modules/stores/incidents';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import * as flowNodeInstanceUtils from 'modules/utils/flowNodeInstance';
import {mockFetchVariables} from 'modules/mocks/api/processInstances/fetchVariables';

import {Paths} from 'modules/Routes';
import {singleInstanceMetadata} from 'modules/mocks/metadata';
import {mockFetchFlowNodeMetadata} from 'modules/mocks/api/processInstances/fetchFlowNodeMetaData';
import {mockModify} from 'modules/mocks/api/processInstances/modify';
import {
  getWrapper,
  mockProcessInstance,
  mockRequests,
  processInstancesMock,
  waitForPollingsToBeComplete,
} from './mocks';
import {modificationsStore} from 'modules/stores/modifications';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockFetchCallHierarchy} from 'modules/mocks/api/v2/processInstances/fetchCallHierarchy';
import {mockFetchProcessInstanceListeners} from 'modules/mocks/api/processInstances/fetchProcessInstanceListeners';
import {noListeners} from 'modules/mocks/mockProcessInstanceListeners';
import {mockFetchFlowNodeInstances} from 'modules/mocks/api/fetchFlowNodeInstances';

const clearPollingStates = () => {
  variablesStore.isPollRequestRunning = false;
  incidentsStore.isPollRequestRunning = false;
  flowNodeInstanceStore.isPollRequestRunning = false;
};

vi.mock('modules/utils/bpmn');
vi.mock('modules/stores/process', () => ({
  processStore: {state: {process: {}}, fetchProcess: vi.fn()},
}));

describe('ProcessInstance - modification mode', () => {
  beforeEach(() => {
    mockRequests();
    modificationsStore.reset();
  });

  it('should display the modifications header and footer when modification mode is enabled', async () => {
    const {user} = render(<ProcessInstance />, {wrapper: getWrapper()});

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

    storeStateLocally({
      [`hideModificationHelperModal`]: true,
    });
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

    storeStateLocally({
      [`hideModificationHelperModal`]: true,
    });
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
    expect(screen.getByTestId('apply-modifications-button')).toBeDisabled();
  });

  it('should display summary modifications modal when apply modifications is clicked during the modification mode', async () => {
    const {user} = render(<ProcessInstance />, {
      wrapper: getWrapper({selectableFlowNode: {flowNodeId: 'taskD'}}),
    });

    expect(await screen.findByText('testVariableName')).toBeInTheDocument();
    storeStateLocally({
      [`hideModificationHelperModal`]: true,
    });
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

  it.skip('should stop polling during the modification mode', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    const handlePollingVariablesSpy = vi.spyOn(variablesStore, 'handlePolling');

    const handlePollingIncidentsSpy = vi.spyOn(incidentsStore, 'handlePolling');

    const initFlowNodeInstanceSpy = vi.spyOn(flowNodeInstanceUtils, 'init');
    const startPollingFlowNodeInstanceSpy = vi.spyOn(
      flowNodeInstanceUtils,
      'startPolling',
    );

    const {user} = render(<ProcessInstance />, {wrapper: getWrapper()});

    storeStateLocally({
      [`hideModificationHelperModal`]: true,
    });

    mockRequests();

    expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(0);
    expect(initFlowNodeInstanceSpy).toHaveBeenCalledTimes(0);
    expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(0);

    clearPollingStates();
    vi.runOnlyPendingTimers();
    await waitFor(() =>
      expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(1),
    );
    expect(initFlowNodeInstanceSpy).toHaveBeenCalledTimes(1);
    expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(1);

    await waitFor(() => {
      expect(variablesStore.state.status).toBe('fetched');
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });

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

    expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(1);
    expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(1);

    clearPollingStates();
    mockRequests();

    vi.runOnlyPendingTimers();

    expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(1);
    expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(1);

    mockRequests();
    await user.click(screen.getByTestId('discard-all-button'));
    await user.click(
      await screen.findByRole('button', {name: /danger discard/i}),
    );

    clearPollingStates();
    mockRequests();

    vi.runOnlyPendingTimers();

    await waitFor(() => {
      expect(startPollingFlowNodeInstanceSpy).toHaveBeenCalledTimes(1);
      expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(3);
    });

    await waitForPollingsToBeComplete();

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should display loading overlay when modifications are applied', async () => {
    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);
    mockModify().withSuccess(
      createBatchOperation({type: 'MODIFY_PROCESS_INSTANCE'}),
    );

    vi.useFakeTimers({shouldAdvanceTime: true});

    const {user} = render(<ProcessInstance />, {
      wrapper: getWrapper({selectableFlowNode: {flowNodeId: 'taskD'}}),
    });

    storeStateLocally({
      [`hideModificationHelperModal`]: true,
    });
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

    expect(
      screen.getByText('Process Instance Modification Mode'),
    ).toBeInTheDocument();

    mockFetchVariables().withSuccess([]);
    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);
    mockFetchProcessInstanceListeners().withSuccess(noListeners);
    mockFetchProcessInstanceListeners().withSuccess(noListeners);
    mockFetchFlowNodeInstances().withSuccess(processInstancesMock.level1);
    mockFetchFlowNodeInstances().withSuccess(processInstancesMock.level1);
    mockFetchFlowNodeInstances().withSuccess(processInstancesMock.level1);
    mockFetchFlowNodeInstances().withSuccess(processInstancesMock.level1);

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
    fireEvent.click(await screen.findByRole('button', {name: 'Apply'}));
    expect(screen.getByTestId('loading-overlay')).toBeInTheDocument();

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('loading-overlay'),
    );

    expect(
      screen.queryByText('Process Instance Modification Mode'),
    ).not.toBeInTheDocument();

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should block navigation when modification mode is enabled', async () => {
    const {user} = render(<ProcessInstance />, {wrapper: getWrapper()});

    storeStateLocally({
      [`hideModificationHelperModal`]: true,
    });

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
    const baseName = contextPath + '/operate';
    vi.stubGlobal('clientConfig', {
      contextPath,
      baseName,
    });

    mockRequests(contextPath);

    const {user} = render(<ProcessInstance />, {
      wrapper: getWrapper({
        initialPath: `${baseName}/processes/4294980768`,
        contextPath: baseName,
      }),
    });

    storeStateLocally({
      [`hideModificationHelperModal`]: true,
    });

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
    const baseName = contextPath + '/operate';
    vi.stubGlobal('clientConfig', {
      contextPath,
      baseName,
    });

    mockRequests(contextPath);

    const {user} = render(
      <>
        <Link to={Paths.dashboard()}>go to dashboard</Link>
        <ProcessInstance />
      </>,
      {
        wrapper: getWrapper({
          initialPath: `${baseName}/processes/4294980768`,
          contextPath: baseName,
        }),
      },
    );

    mockFetchProcessInstance(contextPath).withSuccess(mockProcessInstance);
    mockFetchProcessInstance(contextPath).withSuccess(mockProcessInstance);
    mockFetchCallHierarchy(contextPath).withSuccess([]);
    mockFetchCallHierarchy(contextPath).withSuccess([]);

    storeStateLocally({
      [`hideModificationHelperModal`]: true,
    });

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

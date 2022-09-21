/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Route, MemoryRouter, Routes} from 'react-router-dom';
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
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {createMultiInstanceFlowNodeInstances} from 'modules/testUtils';
import {statistics} from 'modules/mocks/statistics';
import {useNotifications} from 'modules/notifications';
import {LocationLog} from 'modules/utils/LocationLog';
import {modificationsStore} from 'modules/stores/modifications';
import {IS_MODIFICATION_MODE_ENABLED} from 'modules/feature-flags';
import {storeStateLocally} from 'modules/utils/localStorage';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {variablesStore} from 'modules/stores/variables';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {sequenceFlowsStore} from 'modules/stores/sequenceFlows';
import {incidentsStore} from 'modules/stores/incidents';
import {flowNodeStatesStore} from 'modules/stores/flowNodeStates';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {processInstanceDetailsStatisticsStore} from 'modules/stores/processInstanceDetailsStatistics';

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
  flowNodeStatesStore.isPollRequestRunning = false;
  flowNodeInstanceStore.isPollRequestRunning = false;
};

function getWrapper(initialPath: string = '/processes/4294980768') {
  const Wrapper: React.FC<Props> = ({children}) => {
    return (
      <ThemeProvider>
        <MemoryRouter initialEntries={[initialPath]}>
          <Routes>
            <Route path="/processes/:processInstanceId" element={children} />
            <Route path="/processes" element={<>instances page</>} />
          </Routes>
          <LocationLog />
        </MemoryRouter>
      </ThemeProvider>
    );
  };

  return Wrapper;
}

describe('Instance', () => {
  beforeEach(() => {
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res(ctx.text(''))
      ),
      rest.get(
        '/api/process-instances/:instanceId/sequence-flows',
        (_, res, ctx) => res(ctx.json(mockSequenceFlows))
      ),
      rest.post('/api/flow-node-instances', (_, res, ctx) =>
        res(ctx.json(processInstancesMock.level1))
      ),
      rest.get(
        '/api/process-instances/:instanceId/flow-node-states',
        (_, rest, ctx) => rest(ctx.json({taskD: 'INCIDENT'}))
      ),
      rest.get('/api/process-instances/core-statistics', (_, res, ctx) =>
        res(ctx.json(statistics))
      ),
      rest.get(
        '/api/process-instances/:processInstanceId/statistics',
        (_, res, ctx) =>
          res(
            ctx.json([
              {
                activityId: 'taskD',
                active: 1,
                incidents: 1,
                completed: 0,
                canceled: 0,
              },
            ])
          )
      ),
      rest.post('/api/process-instances/:instanceId/variables', (_, res, ctx) =>
        res(
          ctx.json([
            {
              id: '2251799813686037-mwst',
              name: 'newVariable',
              value: '1234',
              scopeId: '2251799813686037',
              processInstanceId: '2251799813686037',
              hasActiveOperation: false,
            },
          ])
        )
      ),
      rest.get('/api/process-instances/:instanceId/incidents', (_, res, ctx) =>
        res(
          ctx.json({
            count: 2,
          })
        )
      )
    );

    modificationsStore.reset();
  });

  it('should render and set the page title', async () => {
    jest.useFakeTimers();

    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(testData.fetch.onPageLoad.processInstance))
      )
    );

    render(<ProcessInstance />, {wrapper: getWrapper()});
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );
    expect(screen.queryByTestId('skeleton-rows')).not.toBeInTheDocument();
    expect(await screen.findByTestId('diagram')).toBeInTheDocument();
    expect(screen.getByTestId('diagram-panel-body')).toBeInTheDocument();
    expect(screen.getByText('Instance History')).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByText('newVariable')).toBeInTheDocument()
    );
    expect(
      within(screen.getByTestId('instance-header')).getByTestId('ACTIVE-icon')
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

    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(ctx.status(404), ctx.json({}))
      )
    );

    render(<ProcessInstance />, {wrapper: getWrapper()});

    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();
    expect(screen.getByTestId('diagram-spinner')).toBeInTheDocument();
    expect(screen.getByTestId('instance-history-skeleton')).toBeInTheDocument();
    expect(screen.getByTestId('skeleton-rows')).toBeInTheDocument();

    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(testData.fetch.onPageLoad.processInstance))
      )
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

    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res(ctx.status(404), ctx.json({}))
      )
    );

    render(<ProcessInstance />, {wrapper: getWrapper('/processes/123')});

    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();
    expect(screen.getByTestId('diagram-spinner')).toBeInTheDocument();
    expect(screen.getByTestId('instance-history-skeleton')).toBeInTheDocument();
    expect(screen.getByTestId('skeleton-rows')).toBeInTheDocument();

    jest.runOnlyPendingTimers();
    jest.runOnlyPendingTimers();
    jest.runOnlyPendingTimers();

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

  (IS_MODIFICATION_MODE_ENABLED ? it : it.skip)(
    'should display the modifications header and footer when modification mode is enabled',
    async () => {
      mockServer.use(
        rest.get('/api/process-instances/:id', (_, res, ctx) =>
          res.once(ctx.json(testData.fetch.onPageLoad.processInstance))
        )
      );

      const {user} = render(<ProcessInstance />, {wrapper: getWrapper()});
      await waitForElementToBeRemoved(
        screen.getByTestId('instance-header-skeleton')
      );

      expect(
        screen.queryByText('Process Instance Modification Mode')
      ).not.toBeInTheDocument();
      expect(
        screen.queryByTestId('discard-all-button')
      ).not.toBeInTheDocument();
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

      expect(
        screen.queryByText('Process Instance Modification Mode')
      ).not.toBeInTheDocument();
      expect(
        screen.queryByTestId('discard-all-button')
      ).not.toBeInTheDocument();
      expect(
        screen.queryByTestId('apply-modifications-button')
      ).not.toBeInTheDocument();
    }
  );

  (IS_MODIFICATION_MODE_ENABLED ? it : it.skip)(
    'should display confirmation modal when discard all is clicked during the modification mode',
    async () => {
      mockServer.use(
        rest.get('/api/process-instances/:id', (_, res, ctx) =>
          res.once(ctx.json(testData.fetch.onPageLoad.processInstance))
        )
      );

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
    }
  );

  (IS_MODIFICATION_MODE_ENABLED ? it : it.skip)(
    'should display no planned modifications modal when apply modifications is clicked during the modification mode',
    async () => {
      mockServer.use(
        rest.get('/api/process-instances/:id', (_, res, ctx) =>
          res.once(ctx.json(testData.fetch.onPageLoad.processInstance))
        )
      );

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
        await screen.findByText(
          /no planned modifications for process instance/i
        )
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
    }
  );

  (IS_MODIFICATION_MODE_ENABLED ? it : it.skip)(
    'should display summary modifications modal when apply modifications is clicked during the modification mode',
    async () => {
      mockServer.use(
        rest.get('/api/process-instances/:id', (_, res, ctx) =>
          res.once(ctx.json(testData.fetch.onPageLoad.processInstance))
        ),
        rest.post(
          '/api/process-instances/:instanceId/flow-node-metadata',
          (_, res, ctx) => res.once(ctx.json(undefined))
        )
      );

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

      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'taskD',
      });

      await user.click(
        screen.getByRole('button', {name: /add single flow node instance/i})
      );

      await user.click(screen.getByTestId('apply-modifications-button'));

      expect(
        await screen.findByText(/Planned modifications for Process Instance/i)
      ).toBeInTheDocument();
      expect(
        screen.getByText(/Click "Apply" to proceed./i)
      ).toBeInTheDocument();

      expect(screen.getByText(/flow node modifications/i)).toBeInTheDocument();

      expect(screen.getByText(/variable modifications/gi)).toBeInTheDocument();

      await user.click(screen.getByRole('button', {name: 'Cancel'}));

      await waitForElementToBeRemoved(() =>
        screen.queryByText(/Planned modifications for Process Instance/i)
      );
    }
  );

  (IS_MODIFICATION_MODE_ENABLED ? it : it.skip)(
    'should display loading overlay when an add token modification is made',
    async () => {
      mockServer.use(
        rest.get('/api/process-instances/:id', (_, res, ctx) =>
          res.once(ctx.json(testData.fetch.onPageLoad.processInstance))
        ),
        rest.post(
          '/api/process-instances/:instanceId/flow-node-metadata',
          (_, res, ctx) => res.once(ctx.json(undefined))
        )
      );

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

      jest.useFakeTimers();

      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'taskD',
      });

      await user.click(
        screen.getByRole('button', {name: /add single flow node instance/i})
      );

      expect(screen.getByText(/adding modifications.../i)).toBeInTheDocument();
      jest.runOnlyPendingTimers();
      expect(
        screen.queryByText(/adding modifications.../i)
      ).not.toBeInTheDocument();

      expect(await screen.findByTestId('badge-plus-icon')).toBeInTheDocument();

      jest.clearAllTimers();
      jest.useRealTimers();
    }
  );

  (IS_MODIFICATION_MODE_ENABLED ? it : it.skip)(
    'should display loading overlay when a cancel token modification is made',
    async () => {
      mockServer.use(
        rest.get('/api/process-instances/:id', (_, res, ctx) =>
          res.once(ctx.json(testData.fetch.onPageLoad.processInstance))
        ),
        rest.post(
          '/api/process-instances/:instanceId/flow-node-metadata',
          (_, res, ctx) => res.once(ctx.json(undefined))
        )
      );

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

      jest.useFakeTimers();

      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'taskD',
      });

      await user.click(
        screen.getByRole('button', {
          name: /cancel all running flow node instances in this flow node/i,
        })
      );
      expect(screen.getByText(/adding modifications.../i)).toBeInTheDocument();
      jest.runOnlyPendingTimers();
      expect(
        screen.queryByText(/adding modifications.../i)
      ).not.toBeInTheDocument();
      expect(await screen.findByTestId('badge-minus-icon')).toBeInTheDocument();

      jest.clearAllTimers();
      jest.useRealTimers();
    }
  );

  (IS_MODIFICATION_MODE_ENABLED ? it : it.skip)(
    'should display loading overlay when a move token modification is made',
    async () => {
      mockServer.use(
        rest.get('/api/process-instances/:id', (_, res, ctx) =>
          res.once(ctx.json(testData.fetch.onPageLoad.processInstance))
        ),
        rest.post(
          '/api/process-instances/:instanceId/flow-node-metadata',
          (_, res, ctx) => res.once(ctx.json(undefined))
        )
      );

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

      jest.useFakeTimers();

      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'taskD',
      });

      await user.click(
        screen.getByRole('button', {
          name: /move all running instances in this flow node to another target/i,
        })
      );
      modificationsStore.finishMovingToken('EndEvent_042s0oc');

      expect(screen.getByText(/adding modifications.../i)).toBeInTheDocument();
      jest.runOnlyPendingTimers();
      expect(
        screen.queryByText(/adding modifications.../i)
      ).not.toBeInTheDocument();
      expect(await screen.findByTestId('badge-minus-icon')).toBeInTheDocument();
      expect(screen.getByTestId('badge-plus-icon')).toBeInTheDocument();

      jest.clearAllTimers();
      jest.useRealTimers();
    }
  );

  (IS_MODIFICATION_MODE_ENABLED ? it : it.skip)(
    'should stop polling during the modification mode',
    async () => {
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
      const handlePollingFlowNodeStatesSpy = jest.spyOn(
        flowNodeStatesStore,
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

      mockServer.use(
        rest.get('/api/process-instances/:id', (_, res, ctx) =>
          res(ctx.json(testData.fetch.onPageLoad.processInstanceWithIncident))
        )
      );

      const {user} = render(<ProcessInstance />, {wrapper: getWrapper()});
      await waitForElementToBeRemoved(
        screen.getByTestId('instance-header-skeleton')
      );

      storeStateLocally({
        [`hideModificationHelperModal`]: true,
      });

      expect(handlePollingSequenceFlowsSpy).toHaveBeenCalledTimes(0);
      expect(handlePollingInstanceDetailsSpy).toHaveBeenCalledTimes(0);
      expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(0);
      expect(handlePollingFlowNodeStatesSpy).toHaveBeenCalledTimes(0);
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
      expect(handlePollingFlowNodeStatesSpy).toHaveBeenCalledTimes(1);
      expect(handlePollingFlowNodeInstanceSpy).toHaveBeenCalledTimes(1);
      expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(1);
      expect(
        handlePollingProcessInstanceDetailStatisticsSpy
      ).toHaveBeenCalledTimes(1);

      await waitFor(() => {
        expect(variablesStore.state.status).toBe('fetched');
        expect(processInstanceDetailsStore.state.status).toBe('fetched');
        expect(flowNodeStatesStore.state.status).toBe('fetched');
        expect(flowNodeInstanceStore.state.status).toBe('fetched');
      });

      await user.click(
        screen.getByRole('button', {
          name: /modify instance/i,
        })
      );

      clearPollingStates();
      jest.runOnlyPendingTimers();

      expect(handlePollingSequenceFlowsSpy).toHaveBeenCalledTimes(1);
      expect(handlePollingInstanceDetailsSpy).toHaveBeenCalledTimes(1);
      expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(1);
      expect(handlePollingFlowNodeStatesSpy).toHaveBeenCalledTimes(1);
      expect(handlePollingFlowNodeInstanceSpy).toHaveBeenCalledTimes(1);
      expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(1);
      expect(
        handlePollingProcessInstanceDetailStatisticsSpy
      ).toHaveBeenCalledTimes(1);

      clearPollingStates();
      jest.runOnlyPendingTimers();

      expect(handlePollingSequenceFlowsSpy).toHaveBeenCalledTimes(1);
      expect(handlePollingInstanceDetailsSpy).toHaveBeenCalledTimes(1);
      expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(1);
      expect(handlePollingFlowNodeStatesSpy).toHaveBeenCalledTimes(1);
      expect(handlePollingFlowNodeInstanceSpy).toHaveBeenCalledTimes(1);
      expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(1);
      expect(
        handlePollingProcessInstanceDetailStatisticsSpy
      ).toHaveBeenCalledTimes(1);

      await user.click(screen.getByTestId('discard-all-button'));
      await user.click(screen.getByTestId('discard-button'));

      clearPollingStates();
      jest.runOnlyPendingTimers();

      await waitFor(() => {
        expect(variablesStore.state.status).toBe('fetched');
        expect(processInstanceDetailsStore.state.status).toBe('fetched');
        expect(flowNodeStatesStore.state.status).toBe('fetched');
        expect(flowNodeInstanceStore.state.status).toBe('fetched');
      });

      expect(handlePollingSequenceFlowsSpy).toHaveBeenCalledTimes(2);
      expect(handlePollingInstanceDetailsSpy).toHaveBeenCalledTimes(2);
      expect(handlePollingIncidentsSpy).toHaveBeenCalledTimes(2);
      expect(handlePollingFlowNodeStatesSpy).toHaveBeenCalledTimes(2);
      expect(handlePollingFlowNodeInstanceSpy).toHaveBeenCalledTimes(2);
      expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(2);
      expect(
        handlePollingProcessInstanceDetailStatisticsSpy
      ).toHaveBeenCalledTimes(2);

      jest.clearAllTimers();
      jest.useRealTimers();
    }
  );

  (IS_MODIFICATION_MODE_ENABLED ? it : it.skip)(
    'should not trigger polling for variables when scope id changed',
    async () => {
      jest.useFakeTimers();

      const handlePollingVariablesSpy = jest.spyOn(
        variablesStore,
        'handlePolling'
      );

      mockServer.use(
        rest.get('/api/process-instances/:id', (_, res, ctx) =>
          res(ctx.json(testData.fetch.onPageLoad.processInstanceWithIncident))
        )
      );

      const {user} = render(<ProcessInstance />, {wrapper: getWrapper()});
      await waitForElementToBeRemoved(
        screen.getByTestId('instance-header-skeleton')
      );

      storeStateLocally({
        [`hideModificationHelperModal`]: true,
      });

      expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(0);

      clearPollingStates();
      jest.runOnlyPendingTimers();

      expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(1);

      await user.click(
        screen.getByRole('button', {
          name: /modify instance/i,
        })
      );

      clearPollingStates();
      jest.runOnlyPendingTimers();

      expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(1);

      clearPollingStates();
      jest.runOnlyPendingTimers();

      expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(1);

      flowNodeSelectionStore.setSelection({
        flowNodeId: 'test',
        flowNodeInstanceId: 'test-id',
      });

      clearPollingStates();
      jest.runOnlyPendingTimers();

      await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));

      expect(handlePollingVariablesSpy).toHaveBeenCalledTimes(1);

      jest.clearAllTimers();
      jest.useRealTimers();
    }
  );
});

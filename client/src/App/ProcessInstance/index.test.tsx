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
        (_, rest, ctx) => rest(ctx.json({}))
      ),
      rest.get('/api/process-instances/core-statistics', (_, res, ctx) =>
        res(ctx.json(statistics))
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

  // TODO: unskip when mocks are removed from modifications
  (IS_MODIFICATION_MODE_ENABLED ? it.skip : it.skip)(
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
});

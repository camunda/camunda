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
} from 'modules/testing-library';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {testData} from './index.setup';
import {mockSequenceFlows} from './TopPanel/index.setup';
import {ProcessInstance} from './index';
import {
  createMultiInstanceFlowNodeInstances,
  createVariable,
} from 'modules/testUtils';
import {LocationLog} from 'modules/utils/LocationLog';
import {modificationsStore} from 'modules/stores/modifications';
import {storeStateLocally} from 'modules/utils/localStorage';
import {createMemoryHistory} from 'history';
import {mockFetchVariables} from 'modules/mocks/api/processInstances/fetchVariables';
import {mockFetchProcessInstanceDetailStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstanceDetailStatistics';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchProcessInstanceIncidents} from 'modules/mocks/api/processInstances/fetchProcessInstanceIncidents';
import {mockFetchSequenceFlows} from 'modules/mocks/api/processInstances/sequenceFlows';
import {mockFetchFlowNodeInstances} from 'modules/mocks/api/fetchFlowNodeInstances';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {mockIncidents} from 'modules/mocks/incidents';

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

function getWrapper(
  initialPath: string = '/carbon/processes/4294980768',
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
            <Route
              path="/carbon/processes/:processInstanceId"
              element={children}
            />
            <Route path="/carbon/processes" element={<>instances page</>} />
            <Route path="/carbon" element={<>dashboard page</>} />
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
        'By leaving this page, all planned modification/s will be discarded.'
      )
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Stay'}));

    expect(
      screen.queryByText(
        'By leaving this page, all planned modification/s will be discarded.'
      )
    ).not.toBeInTheDocument();

    await user.click(
      screen.getByRole('link', {
        description: /View process someProcessName version 1 instances/,
      })
    );

    expect(
      await screen.findByText(
        'By leaving this page, all planned modification/s will be discarded.'
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
      wrapper: getWrapper(
        `${contextPath}/carbon/processes/4294980768`,
        contextPath
      ),
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
        'By leaving this page, all planned modification/s will be discarded.'
      )
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Stay'}));

    expect(
      screen.queryByText(
        'By leaving this page, all planned modification/s will be discarded.'
      )
    ).not.toBeInTheDocument();

    await user.click(
      screen.getByRole('link', {
        description: /View process someProcessName version 1 instances/,
      })
    );

    expect(
      await screen.findByText(
        'By leaving this page, all planned modification/s will be discarded.'
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
        <Link to="/carbon">go to dashboard</Link>
        <ProcessInstance />
      </>,
      {
        wrapper: getWrapper(
          `${contextPath}/carbon/processes/4294980768`,
          contextPath
        ),
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
        'By leaving this page, all planned modification/s will be discarded.'
      )
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Stay'}));

    expect(
      screen.queryByText(
        'By leaving this page, all planned modification/s will be discarded.'
      )
    ).not.toBeInTheDocument();

    await user.click(screen.getByText(/go to dashboard/));

    expect(
      await screen.findByText(
        'By leaving this page, all planned modification/s will be discarded.'
      )
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Leave'}));

    expect(await screen.findByText('dashboard page')).toBeInTheDocument();
  });
});

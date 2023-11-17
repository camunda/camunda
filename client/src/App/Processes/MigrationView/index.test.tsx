/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  unstable_HistoryRouter as HistoryRouter,
  Route,
  Routes,
} from 'react-router-dom';
import {createMemoryHistory} from 'history';
import {act, render, screen, waitFor, within} from 'modules/testing-library';
import {Paths} from 'modules/Routes';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {groupedProcessesMock, mockProcessXML} from 'modules/testUtils';
import {processesStore} from 'modules/stores/processes/processes.migration';

import {AppHeader} from 'App/Layout/AppHeader';
import {Processes} from '../';
import {MigrationView} from '.';
import {useEffect} from 'react';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';

jest.mock('App/Processes/ListView', () => {
  const ListView: React.FC = () => {
    return <>processes page</>;
  };
  return {ListView};
});

function createWrapper(options?: {initialPath?: string; contextPath?: string}) {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    const {initialPath = Paths.processes(), contextPath} = options ?? {};

    useEffect(() => {
      return () => {
        processInstanceMigrationStore.reset();
      };
    }, []);

    return (
      <HistoryRouter
        history={createMemoryHistory({
          initialEntries: [initialPath],
        })}
        basename={contextPath ?? ''}
      >
        <Routes>
          <Route
            path={Paths.processes()}
            element={
              <>
                <AppHeader />
                {children}
              </>
            }
          />
          <Route
            path={Paths.dashboard()}
            element={
              <>
                <AppHeader />
                dashboard page
              </>
            }
          />
        </Routes>
      </HistoryRouter>
    );
  };
  return Wrapper;
}

describe('MigrationView', () => {
  beforeEach(() => {
    processInstanceMigrationStore.enable();
  });

  afterEach(() => {
    window.clientConfig = undefined;
  });

  it.each(['/custom', ''])(
    'should block navigation to dashboard page when migration mode is enabled - context path: %p',
    async (contextPath) => {
      window.clientConfig = {
        contextPath,
      };
      mockFetchGroupedProcesses(contextPath).withSuccess([]);

      const {user} = render(<Processes />, {
        wrapper: createWrapper({
          initialPath: '/processes?process=demoProcess&version=1',
        }),
      });

      expect(
        screen.getByText('Migration Step 1 - Mapping elements'),
      ).toBeInTheDocument();

      await user.click(
        within(
          screen.getByRole('navigation', {
            name: /camunda operate/i,
          }),
        ).getByRole('link', {
          name: /dashboard/i,
        }),
      );

      expect(
        await screen.findByText(
          /By leaving this page, all planned mapping\/s will be discarded/,
        ),
      ).toBeInTheDocument();
      await user.click(screen.getByRole('button', {name: 'Stay'}));

      expect(
        screen.queryByText(
          /By leaving this page, all planned mapping\/s will be discarded/,
        ),
      ).not.toBeInTheDocument();

      await user.click(
        within(
          screen.getByRole('navigation', {
            name: /camunda operate/i,
          }),
        ).getByRole('link', {
          name: /dashboard/i,
        }),
      );

      expect(
        await screen.findByText(
          /By leaving this page, all planned mapping\/s will be discarded/,
        ),
      ).toBeInTheDocument();

      await user.click(screen.getByRole('button', {name: 'Leave'}));

      expect(await screen.findByText(/dashboard page/i)).toBeInTheDocument();
    },
  );

  it.each(['/custom', ''])(
    'should block navigation to processes page when migration mode is enabled - context path: %p',

    async (contextPath) => {
      window.clientConfig = {
        contextPath,
      };
      mockFetchGroupedProcesses(contextPath).withSuccess([]);

      const {user} = render(<Processes />, {
        wrapper: createWrapper({
          initialPath: '/processes?process=demoProcess&version=1',
        }),
      });

      expect(
        screen.getByText('Migration Step 1 - Mapping elements'),
      ).toBeInTheDocument();

      await user.click(
        within(
          screen.getByRole('navigation', {
            name: /camunda operate/i,
          }),
        ).getByRole('link', {
          name: /processes/i,
        }),
      );

      expect(
        await screen.findByText(
          /By leaving this page, all planned mapping\/s will be discarded/,
        ),
      ).toBeInTheDocument();
      await user.click(screen.getByRole('button', {name: 'Stay'}));

      expect(
        screen.queryByText(
          /By leaving this page, all planned mapping\/s will be discarded/,
        ),
      ).not.toBeInTheDocument();

      await user.click(
        within(
          screen.getByRole('navigation', {
            name: /camunda operate/i,
          }),
        ).getByRole('link', {
          name: /processes/i,
        }),
      );

      expect(
        await screen.findByText(
          /By leaving this page, all planned mapping\/s will be discarded/,
        ),
      ).toBeInTheDocument();

      await user.click(screen.getByRole('button', {name: 'Leave'}));

      expect(await screen.findByText(/processes page/i)).toBeInTheDocument();
    },
  );

  it('should render summary notification in summary step', async () => {
    const queryString =
      '?active=true&incidents=true&process=demoProcess&version=3';

    const originalWindow = {...window};

    const locationSpy = jest.spyOn(window, 'location', 'get');

    locationSpy.mockImplementation(() => ({
      ...originalWindow.location,
      search: queryString,
    }));

    processInstanceMigrationStore.setSelectedInstancesCount(7);
    processInstanceMigrationStore.setCurrentStep('summary');
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);

    render(<MigrationView />);

    await waitFor(() => {
      expect(processesStore.state.status).toBe('fetched');
    });

    act(() => {
      processesStore.setSelectedTargetProcess('{bigVarProcess}-{<default>}');
      processesStore.setSelectedTargetVersion(1);
    });

    mockFetchProcessXML().withSuccess(mockProcessXML);

    expect(
      screen.getByText(
        /You are about to migrate 7 process instances from the process definition:/i,
      ),
    ).toBeInTheDocument();

    expect(
      await screen.findByText(/New demo process - version 3/i),
    ).toBeInTheDocument();

    expect(screen.getByText(/to the process definition:/i)).toBeInTheDocument();

    expect(
      screen.getByText(/Big variable process - version 1/i),
    ).toBeInTheDocument();

    expect(
      screen.getByText(
        /This process can take several minutes until it completes. You can observe progress of this in the operations panel./i,
      ),
    ).toBeInTheDocument();

    expect(
      screen.getByText(
        /The flow nodes listed below will be mapped from the source on the left side to target on the right side./i,
      ),
    ).toBeInTheDocument();

    locationSpy.mockClear();
  });
});

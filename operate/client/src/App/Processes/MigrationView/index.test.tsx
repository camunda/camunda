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
  unstable_HistoryRouter as HistoryRouter,
  MemoryRouter,
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

    render(<MigrationView />, {wrapper: MemoryRouter});

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

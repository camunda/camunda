/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  unstable_HistoryRouter as HistoryRouter,
  Route,
  Routes,
} from 'react-router-dom';
import {createMemoryHistory} from 'history';
import {render, screen, within} from 'modules/testing-library';
import {Paths} from 'modules/Routes';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';

import {AppHeader} from 'App/Layout/AppHeader';
import {Processes} from '../';
import {useEffect} from 'react';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {QueryClientProvider} from '@tanstack/react-query';

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
      <QueryClientProvider client={getMockQueryClient()}>
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
      </QueryClientProvider>
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
        screen.getByText(/migration step 1 - mapping elements/i),
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
        screen.getByText(/migration step 1 - mapping elements/i),
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
});

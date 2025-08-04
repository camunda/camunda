/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createMemoryRouter, RouterProvider} from 'react-router-dom';
import {render, screen, within} from 'modules/testing-library';
import {Paths} from 'modules/Routes';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';

import {AppHeader} from 'App/Layout/AppHeader';
import {Processes} from '../../index';
import {useEffect} from 'react';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {QueryClientProvider} from '@tanstack/react-query';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {open} from 'modules/mocks/diagrams';
import {createUser} from 'modules/testUtils';
import {mockMe} from 'modules/mocks/api/v2/me';

vi.mock('App/Processes/ListView', () => {
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

    const router = createMemoryRouter(
      [
        {
          path: Paths.processes(),
          element: (
            <>
              <AppHeader />
              {children}
            </>
          ),
        },
        {
          path: Paths.dashboard(),
          element: (
            <>
              <AppHeader />
              dashboard page
            </>
          ),
        },
      ],
      {
        initialEntries: [initialPath],
        basename: contextPath ?? '',
      },
    );

    return (
      <QueryClientProvider client={getMockQueryClient()}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    );
  };
  return Wrapper;
}

describe('MigrationView', () => {
  beforeEach(() => {
    processInstanceMigrationStore.enable();
    mockMe().withSuccess(createUser({authorizedApplications: ['operate']}));
    mockMe({contextPath: '/custom'}).withSuccess(
      createUser({authorizedApplications: ['operate']}),
    );
  });

  it.each(['/custom', ''])(
    'should block navigation to dashboard page when migration mode is enabled - context path: %p',
    async (contextPath) => {
      vi.stubGlobal('clientConfig', {
        contextPath,
      });
      mockFetchProcessDefinitionXml({contextPath}).withSuccess(
        open('orderProcess.bpmn'),
      );
      processInstanceMigrationStore.setSourceProcessDefinitionKey('1');
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
      vi.stubGlobal('clientConfig', {
        contextPath,
      });
      mockFetchProcessDefinitionXml({contextPath}).withSuccess(
        open('orderProcess.bpmn'),
      );
      processInstanceMigrationStore.setSourceProcessDefinitionKey('1');
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

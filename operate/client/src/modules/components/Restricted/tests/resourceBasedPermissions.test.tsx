/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Restricted} from '../index';
import {render, screen} from 'modules/testing-library';
import {authenticationStore} from 'modules/stores/authentication';
import {useEffect} from 'react';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {createUser, groupedProcessesMock} from 'modules/testUtils';
import {processesStore} from 'modules/stores/processes/processes.list';
import {Paths} from 'modules/Routes';
import {mockMe} from 'modules/mocks/api/v2/me';

const createWrapper = (initialPath: string = Paths.processes()) => {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        authenticationStore.reset();
        processesStore.reset();
      };
    }, []);

    return (
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route path={Paths.processes()} element={children} />
        </Routes>
      </MemoryRouter>
    );
  };

  return Wrapper;
};

describe('Restricted', () => {
  beforeEach(() => {
    vi.stubGlobal('clientConfig', {
      resourcePermissionsEnabled: true,
    });
  });

  it('should show restricted content if user has write permissions and no restricted resource based scopes defined', async () => {
    mockMe().withSuccess(createUser());
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    await processesStore.fetchProcesses();

    const {rerender} = render(
      <Restricted>
        <div>test content</div>
      </Restricted>,
      {wrapper: createWrapper()},
    );

    expect(screen.getByText('test content')).toBeInTheDocument();

    rerender(
      <Restricted
        resourceBasedRestrictions={{
          scopes: [],
          permissions: processesStore.getPermissions('demoProcess'),
        }}
      >
        <div>test content</div>
      </Restricted>,
    );

    expect(screen.getByText('test content')).toBeInTheDocument();
  });

  it('should render restricted content when resource based permissions are disabled', async () => {
    mockMe().withSuccess(createUser());
    vi.stubGlobal('clientConfig', {
      resourcePermissionsEnabled: false,
    });

    render(
      <Restricted
        resourceBasedRestrictions={{
          scopes: ['DELETE'],
          permissions: processesStore.getPermissions('demoProcess'),
        }}
      >
        <div>test content</div>
      </Restricted>,
      {wrapper: createWrapper()},
    );

    expect(screen.getByText('test content')).toBeInTheDocument();
  });

  it('should render restricted content in processes page', async () => {
    mockMe().withSuccess(createUser());

    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    await processesStore.fetchProcesses();

    const {rerender} = render(
      <Restricted
        resourceBasedRestrictions={{
          scopes: ['UPDATE_PROCESS_INSTANCE'],
          permissions: processesStore.getPermissions('demoProcess'),
        }}
      >
        <div>test content</div>
      </Restricted>,
      {wrapper: createWrapper()},
    );

    expect(screen.getByText('test content')).toBeInTheDocument();

    rerender(
      <Restricted
        resourceBasedRestrictions={{
          scopes: ['DELETE'],
          permissions: processesStore.getPermissions('demoProcess'),
        }}
      >
        <div>test content</div>
      </Restricted>,
    );

    expect(screen.queryByText('test content')).not.toBeInTheDocument();

    rerender(
      <Restricted
        resourceBasedRestrictions={{
          scopes: ['DELETE'],
          permissions: processesStore.getPermissions(
            'eventBasedGatewayProcess',
          ),
        }}
      >
        <div>test content</div>
      </Restricted>,
    );

    expect(screen.getByText('test content')).toBeInTheDocument();

    rerender(
      <Restricted
        resourceBasedRestrictions={{
          scopes: ['DELETE_PROCESS_INSTANCE'],
          permissions: processesStore.getPermissions('bigVarProcess'),
        }}
      >
        <div>test content</div>
      </Restricted>,
    );

    expect(screen.getByText('test content')).toBeInTheDocument();

    rerender(
      <Restricted
        resourceBasedRestrictions={{
          scopes: ['DELETE_PROCESS_INSTANCE'],
          permissions: processesStore.getPermissions('orderProcess'),
        }}
      >
        <div>test content</div>
      </Restricted>,
    );

    expect(screen.queryByText('test content')).not.toBeInTheDocument();
  });
});

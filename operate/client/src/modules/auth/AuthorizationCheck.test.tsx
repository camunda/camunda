/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {AuthorizationCheck} from './AuthorizationCheck';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {Paths} from 'modules/Routes';
import {createUser} from 'modules/testUtils';
import {mockMe} from 'modules/mocks/api/v2/me';

const FORBIDDEN_CONTENT = 'Forbidden content';
const AUTHORIZED_CONTENT = 'Authorized content';

const getWrapper = () => {
  const mockClient = getMockQueryClient();

  type Props = {
    children?: React.ReactNode;
  };

  const Wrapper: React.FC<Props> = ({children}) => {
    return (
      <QueryClientProvider client={mockClient}>
        <MemoryRouter>
          <Routes>
            <Route
              path={Paths.forbidden()}
              element={<h1>{FORBIDDEN_CONTENT}</h1>}
            />
            <Route path="*" element={children} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );
  };

  return Wrapper;
};

describe('<AuthorizationCheck />', () => {
  beforeEach(() => {
    mockMe().withSuccess(createUser({authorizedApplications: ['operate']}));
  });

  it('should show the provided content', async () => {
    mockMe().withSuccess(createUser({authorizedApplications: ['operate']}));

    render(
      <AuthorizationCheck>
        <h1>{AUTHORIZED_CONTENT}</h1>
      </AuthorizationCheck>,
      {wrapper: getWrapper()},
    );

    expect(screen.getByText(AUTHORIZED_CONTENT)).toBeInTheDocument();
  });

  it('should redirect when user is not authorized', async () => {
    mockMe().withSuccess(createUser({authorizedApplications: ['tasklist']}));

    render(
      <AuthorizationCheck>
        <h1>{AUTHORIZED_CONTENT}</h1>
      </AuthorizationCheck>,
      {wrapper: getWrapper()},
    );

    expect(await screen.findByText(FORBIDDEN_CONTENT)).toBeInTheDocument();
  });
});

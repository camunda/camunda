/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {authenticationStore} from 'modules/stores/authentication';
import {LocationLog} from 'modules/utils/LocationLog';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {AuthorizationCheck} from './AuthorizationCheck';
import {mockMe} from 'modules/mocks/api/v2/me';
import {createUser} from 'modules/testUtils';
import {Paths} from 'modules/Routes';

const FORBIDDEN_CONTENT = 'Forbidden content';
const AUTHORIZED_CONTENT = 'Authorized content';

function createWrapper() {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => (
    <MemoryRouter>
      <Routes>
        <Route
          path={Paths.forbidden()}
          element={<h1>{FORBIDDEN_CONTENT}</h1>}
        />
        <Route path="*" element={children} />
      </Routes>
      <LocationLog />
    </MemoryRouter>
  );

  return Wrapper;
}

describe('<AuthorizationCheck />', () => {
  beforeEach(() => {
    authenticationStore.reset();
  });

  it('should render children when user is authorized', async () => {
    mockMe().withSuccess(createUser({authorizedApplications: ['operate']}));
    await authenticationStore.authenticate();

    render(
      <AuthorizationCheck>
        <h1>{AUTHORIZED_CONTENT}</h1>
      </AuthorizationCheck>,
      {wrapper: createWrapper()},
    );

    expect(screen.getByText(AUTHORIZED_CONTENT)).toBeInTheDocument();
    expect(screen.queryByText(FORBIDDEN_CONTENT)).not.toBeInTheDocument();
  });

  it('should redirect when user is not authorized', async () => {
    mockMe().withSuccess(createUser({authorizedApplications: ['tasklist']}));
    await authenticationStore.authenticate();

    render(
      <AuthorizationCheck>
        <h1>{AUTHORIZED_CONTENT}</h1>
      </AuthorizationCheck>,
      {wrapper: createWrapper()},
    );

    expect(screen.queryByText(AUTHORIZED_CONTENT)).not.toBeInTheDocument();
    expect(screen.getByText(FORBIDDEN_CONTENT)).toBeInTheDocument();
  });
});

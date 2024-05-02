/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {AuthenticationCheck} from './index';
import {authenticationStore} from 'modules/stores/authentication';

const LOGIN_CONTENT = 'Login content';
const LOGIN_PATH = '/login';

type Props = {
  children?: React.ReactNode;
};

const Wrapper: React.FC<Props> = ({children}) => {
  return (
    <MemoryRouter>
      <Routes>
        <Route path={LOGIN_PATH} element={<h1>{LOGIN_CONTENT}</h1>} />
        <Route path="*" element={children} />
      </Routes>
    </MemoryRouter>
  );
};

describe('<AuthenticationCheck />', () => {
  afterEach(() => {
    authenticationStore.reset();
  });

  it('should show the provided content', () => {
    const CONTENT = 'Secret route';

    render(
      <AuthenticationCheck redirectPath={LOGIN_PATH}>
        <h1>{CONTENT}</h1>
      </AuthenticationCheck>,
      {
        wrapper: Wrapper,
      },
    );

    expect(screen.getByText(CONTENT)).toBeInTheDocument();
    expect(screen.queryByText(LOGIN_CONTENT)).not.toBeInTheDocument();
  });

  it('should redirect when not authenticated', () => {
    authenticationStore.disableSession();

    const CONTENT = 'Secret route';

    render(
      <AuthenticationCheck redirectPath={LOGIN_PATH}>
        <h1>{CONTENT}</h1>
      </AuthenticationCheck>,
      {wrapper: Wrapper},
    );

    expect(screen.queryByText(CONTENT)).not.toBeInTheDocument();
    expect(screen.getByText(LOGIN_CONTENT)).toBeInTheDocument();
  });
});

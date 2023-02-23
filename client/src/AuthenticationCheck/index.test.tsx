/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {AuthenticationCheck} from './index';
import {authenticationStore} from 'modules/stores/authentication';

const LOGIN_CONTENT = 'Login content';
const fetchMock = jest.spyOn(window, 'fetch');
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
  beforeAll(() => {
    global.IS_REACT_ACT_ENVIRONMENT = false;
  });

  afterEach(() => {
    fetchMock.mockClear();
    authenticationStore.reset();
  });

  afterAll(() => {
    fetchMock.mockRestore();
    global.IS_REACT_ACT_ENVIRONMENT = true;
  });

  it('should show the provided content', async () => {
    fetchMock.mockResolvedValueOnce(new Response(undefined, {status: 204}));

    const CONTENT = 'Secret route';

    await authenticationStore.handleLogin('demo', 'demo');

    render(
      <AuthenticationCheck redirectPath={LOGIN_PATH}>
        <h1>{CONTENT}</h1>
      </AuthenticationCheck>,
      {wrapper: Wrapper},
    );

    expect(screen.getByText(CONTENT)).toBeInTheDocument();
  });

  it('should redirect when not authenticated', async () => {
    authenticationStore.disableSession();

    render(
      <AuthenticationCheck redirectPath={LOGIN_PATH}>
        <h1>Secret route</h1>
      </AuthenticationCheck>,
      {wrapper: Wrapper},
    );

    expect(await screen.findByText(LOGIN_CONTENT)).toBeInTheDocument();
  });
});

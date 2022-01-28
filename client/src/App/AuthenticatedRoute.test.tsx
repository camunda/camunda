/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {render, screen, waitFor} from '@testing-library/react';
import {createMemoryHistory} from 'history';
import {mockServer} from 'modules/mock-server/node';
import {authenticationStore} from 'modules/stores/authentication';
import {rest} from 'msw';
import {Router, Switch} from 'react-router-dom';
import {AuthenticatedRoute} from './AuthenticatedRoute';

const PROTECTED_CONTENT = 'protected content';
const PUBLIC_AREA_URL = '/public-area';
const PROTECTED_AREA_URL = '/protected-area';
function createWrapper(
  history = createMemoryHistory({initialEntries: [PROTECTED_AREA_URL]})
) {
  const Wrapper: React.FC = ({children}) => (
    <Router history={history}>
      <Switch>{children}</Switch>
    </Router>
  );
  return Wrapper;
}

describe('<AuthenticatedRoute />', () => {
  afterEach(() => {
    authenticationStore.reset();
  });

  it('should handle unknown session', async () => {
    mockServer.use(
      rest.get('/api/authentications/user', (_, res, ctx) =>
        res.once(ctx.status(401))
      )
    );
    const history = createMemoryHistory<{referrer: Location}>({
      initialEntries: [PROTECTED_AREA_URL],
    });
    render(
      <AuthenticatedRoute
        redirectPath={PUBLIC_AREA_URL}
        path={PROTECTED_AREA_URL}
        exact
      >
        {PROTECTED_CONTENT}
      </AuthenticatedRoute>,
      {
        wrapper: createWrapper(history),
      }
    );

    expect(screen.getByText(PROTECTED_CONTENT)).toBeInTheDocument();
    await waitFor(() =>
      expect(history.location.pathname).toBe(PUBLIC_AREA_URL)
    );
    expect(screen.queryByText(PROTECTED_CONTENT)).not.toBeInTheDocument();
    expect(history.location.state.referrer.pathname).toBe(PROTECTED_AREA_URL);
  });

  it('should handle no authentication', async () => {
    const history = createMemoryHistory<{referrer: Location}>({
      initialEntries: [PROTECTED_AREA_URL],
    });

    authenticationStore.disableSession();

    render(
      <AuthenticatedRoute
        redirectPath={PUBLIC_AREA_URL}
        path={PROTECTED_AREA_URL}
        exact
      >
        {PROTECTED_CONTENT}
      </AuthenticatedRoute>,
      {
        wrapper: createWrapper(history),
      }
    );

    await waitFor(() =>
      expect(history.location.pathname).toBe(PUBLIC_AREA_URL)
    );
  });
});

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'common/testing/testing-library';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {AuthorizationCheck} from './index';
import {pages} from 'common/routing';
import {vi} from 'vitest';
import {nodeMockServer} from 'common/testing/nodeMockServer';
import {http, HttpResponse} from 'msw';
import * as userMocks from 'common/mocks/current-user';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'common/testing/getMockQueryClient';

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
              path={pages.forbidden}
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
  afterEach(() => {
    vi.resetAllMocks();
  });

  beforeEach(() => {
    nodeMockServer.use(
      http.get(
        '/v2/license',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {
          once: true,
        },
      ),
    );
  });

  it('should show the provided content', async () => {
    render(
      <AuthorizationCheck>
        <h1>{AUTHORIZED_CONTENT}</h1>
      </AuthorizationCheck>,
      {wrapper: getWrapper()},
    );

    expect(screen.getByText(AUTHORIZED_CONTENT)).toBeInTheDocument();
  });

  it('should redirect when user is not authorized', async () => {
    nodeMockServer.use(
      http.get(
        '/v2/authentication/me',
        () => {
          return HttpResponse.json(userMocks.currentUnauthorizedUser);
        },
        {
          once: true,
        },
      ),
    );

    render(
      <AuthorizationCheck>
        <h1>{AUTHORIZED_CONTENT}</h1>
      </AuthorizationCheck>,
      {wrapper: getWrapper()},
    );

    expect(await screen.findByText(FORBIDDEN_CONTENT)).toBeInTheDocument();
  });
});

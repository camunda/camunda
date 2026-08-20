/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HttpResponse, http} from 'msw';
import {QueryClientProvider} from '@tanstack/react-query';
import {MemoryRouter, Routes, Route} from 'react-router-dom';
import {render, screen, waitFor} from 'modules/testing/testing-library';
import {getMockQueryClient} from 'modules/testing/getMockQueryClient';
import {nodeMockServer} from 'modules/testing/nodeMockServer';
import {LocationLog} from 'modules/testing/LocationLog';
import {currentUser} from '@camunda/c8-mocks';
import * as taskMocks from 'modules/mocks/task';
import {Component} from '.';

const getWrapper = (id: string = '0', search: string = '') => {
  const mockClient = getMockQueryClient();

  const Wrapper: React.FC<{
    children?: React.ReactNode;
  }> = ({children}) => (
    <QueryClientProvider client={mockClient}>
      <MemoryRouter initialEntries={[`/${id}${search}`]}>
        <Routes>
          <Route path="/:id" element={children} />
        </Routes>
        <LocationLog />
      </MemoryRouter>
    </QueryClientProvider>
  );

  return Wrapper;
};

describe('Task Details', () => {
  beforeEach(() => {
    nodeMockServer.use(
      http.get('/v2/authentication/me', () => HttpResponse.json(currentUser), {
        once: true,
      }),
    );
  });

  it('navigates to initial page preserving search params when task is cancelled', async () => {
    nodeMockServer.use(
      http.get(
        '/v2/user-tasks/:userTaskKey',
        () => HttpResponse.json(taskMocks.assignedTask({state: 'CANCELED'})),
        {once: true},
      ),
    );

    render(<Component />, {
      wrapper: getWrapper('0', '?filter=all-open'),
    });

    await waitFor(() => {
      expect(screen.getByTestId('pathname')).toHaveTextContent('/');
      expect(screen.getByTestId('search')).toHaveTextContent(
        '?filter=all-open',
      );
    });
  });
});

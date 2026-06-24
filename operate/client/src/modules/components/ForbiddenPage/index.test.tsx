/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {render, screen} from 'modules/testing-library';
import {ForbiddenPage} from './index';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockMe} from 'modules/mocks/api/v2/me';
import {createUser} from 'modules/testUtils';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return (
    <QueryClientProvider client={getMockQueryClient()}>
      <MemoryRouter>
        <Routes>
          <Route path="/" element={children} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
};

describe('Forbidden', () => {
  it('should render Forbidden component with correct text and link', async () => {
    mockMe().withSuccess(createUser());
    render(<ForbiddenPage />, {wrapper: Wrapper});

    expect(screen.getByRole('banner')).toBeInTheDocument();
    expect(
      screen.getByText('You don’t have access to this component'),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('link', {
        name: /Learn more about roles and permissions/i,
      }),
    ).toBeInTheDocument();
  });
});

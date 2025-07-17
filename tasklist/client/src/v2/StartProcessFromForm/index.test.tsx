/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'common/testing/testing-library';
import {Component} from './index';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'common/testing/getMockQueryClient';

const getWrapper = ({
  initialEntries,
}: Pick<React.ComponentProps<typeof MemoryRouter>, 'initialEntries'>) => {
  const mockClient = getMockQueryClient();

  const Wrapper: React.FC<{
    children?: React.ReactNode;
  }> = ({children}) => (
    <QueryClientProvider client={mockClient}>
      <MemoryRouter initialEntries={initialEntries}>
        <Routes>
          <Route path="/new/:bpmnProcessId" element={children} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );

  return Wrapper;
};

describe('<StartProcessFromForm /> V2', () => {
  it('should show V2 API not supported error message', async () => {
    render(<Component />, {
      wrapper: getWrapper({
        initialEntries: ['/new/foo'],
      }),
    });

    expect(
      await screen.findByRole('heading', {
        name: 'Public forms not supported',
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'Public forms are not supported for this version. Please contact your administrator.',
      ),
    ).toBeInTheDocument();
  });
});

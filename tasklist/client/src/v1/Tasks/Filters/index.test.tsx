/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {screen} from '@testing-library/react';
import {render} from 'common/testing/testing-library';
import {LocationLog} from 'common/testing/LocationLog';
import {MemoryRouter} from 'react-router-dom';
import {Filters} from './index';
import {nodeMockServer} from 'common/testing/nodeMockServer';
import {HttpResponse, http} from 'msw';
import * as userMocks from 'common/mocks/current-user';

const createWrapper = (
  initialEntries: React.ComponentProps<
    typeof MemoryRouter
  >['initialEntries'] = ['/'],
) => {
  const Wrapper: React.FC<{
    children?: React.ReactNode;
  }> = ({children}) => (
    <MemoryRouter initialEntries={initialEntries}>
      {children}
      <LocationLog />
    </MemoryRouter>
  );

  return Wrapper;
};

describe('<Filters />', () => {
  beforeEach(() => {
    nodeMockServer.use(
      http.get(
        '/v2/authentication/me',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
    );
  });

  it('should render filters', async () => {
    const {user} = render(<Filters disabled={false} />, {
      wrapper: createWrapper(),
    });

    expect(
      screen.getByRole('heading', {name: /all open tasks/i}),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /sort tasks/i}));

    expect(screen.getByText('Creation date')).toBeInTheDocument();
    expect(screen.getByText('Follow-up date')).toBeInTheDocument();
    expect(screen.getByText('Due date')).toBeInTheDocument();
    expect(screen.queryByText('Completion date')).not.toBeInTheDocument();
  });

  it('should enable sorting by completion date', async () => {
    const {user} = render(<Filters disabled={false} />, {
      wrapper: createWrapper(['/?filter=completed']),
    });

    await user.click(screen.getByRole('button', {name: /sort tasks/i}));

    expect(screen.getByText('Completion date')).toBeInTheDocument();
  });

  it('should persist sorting in the URL', async () => {
    const {user} = render(<Filters disabled={false} />, {
      wrapper: createWrapper(['/?filter=assigned-to-me']),
    });

    await user.click(screen.getByRole('button', {name: /sort tasks/i}));

    await user.click(screen.getByText('Due date'));

    expect(screen.getByTestId('search')).toHaveTextContent(
      '?filter=assigned-to-me&sortBy=due',
    );
  });

  it('should disable sorting controls', () => {
    render(<Filters disabled={true} />, {
      wrapper: createWrapper(),
    });

    expect(screen.getByRole('button', {name: /sort tasks/i})).toBeDisabled();
  });

  it('should render the correct filter label', async () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <Filters disabled={false} />
      </MemoryRouter>,
    );

    expect(
      screen.getByRole('heading', {name: /all open tasks/i}),
    ).toBeInTheDocument();

    render(
      <MemoryRouter initialEntries={['/?filter=assigned-to-me']}>
        <Filters disabled={false} />
      </MemoryRouter>,
    );

    expect(
      await screen.findByRole('heading', {name: /assigned to me/i}),
    ).toBeInTheDocument();

    render(
      <MemoryRouter initialEntries={['/?filter=unassigned']}>
        <Filters disabled={false} />
      </MemoryRouter>,
    );

    expect(
      screen.getByRole('heading', {name: /unassigned/i}),
    ).toBeInTheDocument();

    render(
      <MemoryRouter initialEntries={['/?filter=completed']}>
        <Filters disabled={false} />
      </MemoryRouter>,
    );

    expect(
      screen.getByRole('heading', {name: /completed/i}),
    ).toBeInTheDocument();

    render(
      <MemoryRouter initialEntries={['/?filter=custom']}>
        <Filters disabled={false} />
      </MemoryRouter>,
    );
  });
});

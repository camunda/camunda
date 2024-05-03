/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {screen, fireEvent} from '@testing-library/react';
import {render} from 'modules/testing-library';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {LocationLog} from 'modules/utils/LocationLog';
import {MemoryRouter} from 'react-router-dom';
import {Filters} from './index';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/getMockQueryClient';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {HttpResponse, http} from 'msw';
import * as userMocks from 'modules/mock-schema/mocks/current-user';
import {storeStateLocally} from 'modules/utils/localStorage';

const createWrapper = (
  initialEntries: React.ComponentProps<
    typeof MemoryRouter
  >['initialEntries'] = ['/'],
) => {
  const mockClient = getMockQueryClient();

  const Wrapper: React.FC<{
    children?: React.ReactNode;
  }> = ({children}) => (
    <QueryClientProvider client={mockClient}>
      <MockThemeProvider>
        <MemoryRouter initialEntries={initialEntries}>
          {children}
          <LocationLog />
        </MemoryRouter>
      </MockThemeProvider>
    </QueryClientProvider>
  );

  return Wrapper;
};

describe('<Filters />', () => {
  beforeEach(() => {
    nodeMockServer.use(
      http.get(
        '/v1/internal/users/current',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
    );
  });

  it('should filters', () => {
    render(<Filters disabled={false} />, {
      wrapper: createWrapper(),
    });

    fireEvent.click(screen.getByRole('combobox', {name: 'Filter options'}));

    expect(
      screen.getByRole('option', {name: 'All open tasks'}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('option', {name: 'Assigned to me'}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('option', {name: 'Unassigned'}),
    ).toBeInTheDocument();
    expect(screen.getByRole('option', {name: 'Completed'})).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', {name: 'Sort tasks'}));

    expect(screen.getByText('Creation date')).toBeInTheDocument();
    expect(screen.getByText('Follow-up date')).toBeInTheDocument();
    expect(screen.getByText('Due date')).toBeInTheDocument();
  });

  it('should load values from URL', () => {
    render(<Filters disabled={false} />, {
      wrapper: createWrapper(['/?filter=completed&sortBy=creation']),
    });

    expect(screen.getByText('Completed')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', {name: 'Sort tasks'}));

    expect(screen.getByText('Creation date')).toBeInTheDocument();
  });

  it('should write changes to the URL', () => {
    render(<Filters disabled={false} />, {
      wrapper: createWrapper(),
    });

    fireEvent.click(screen.getByRole('combobox', {name: 'Filter options'}));
    fireEvent.click(screen.getByRole('option', {name: 'Assigned to me'}));
    fireEvent.click(screen.getByRole('button', {name: 'Sort tasks'}));
    fireEvent.click(screen.getByText('Due date'));

    expect(screen.getByTestId('search')).toHaveTextContent(
      '?filter=assigned-to-me&sortBy=due',
    );
  });

  it('should disable filters', () => {
    render(<Filters disabled={true} />, {
      wrapper: createWrapper(),
    });

    expect(
      screen.getByRole('combobox', {name: 'Filter options'}),
    ).toBeDisabled();
    expect(screen.getByRole('button', {name: 'Sort tasks'})).toBeDisabled();
  });

  it('should replace old claimed by me param', () => {
    render(<Filters disabled={false} />, {
      wrapper: createWrapper(['/?filter=claimed-by-me']),
    });

    expect(screen.getByTestId('search')).toHaveTextContent(
      '?filter=assigned-to-me',
    );
  });

  it('should replace old unclaimed param', () => {
    render(<Filters disabled={false} />, {
      wrapper: createWrapper(['/?filter=unclaimed']),
    });

    expect(screen.getByTestId('search')).toHaveTextContent(
      '?filter=unassigned',
    );
  });

  it('should sort by completion date', () => {
    render(<Filters disabled={false} />, {
      wrapper: createWrapper(),
    });

    fireEvent.click(screen.getByRole('combobox', {name: 'Filter options'}));
    fireEvent.click(screen.getByRole('option', {name: 'Completed'}));

    expect(screen.getByTestId('search')).toHaveTextContent(
      '?filter=completed&sortBy=completion',
    );
  });

  it('should remove sorting by completion date', () => {
    render(<Filters disabled={false} />, {
      wrapper: createWrapper(['/?filter=completed&sortBy=completion']),
    });

    fireEvent.click(screen.getByRole('combobox', {name: 'Filter options'}));
    fireEvent.click(screen.getByRole('option', {name: 'All open tasks'}));

    expect(screen.getByTestId('search')).toHaveTextContent(
      '?filter=all-open&sortBy=creation',
    );
  });

  it('should load custom filters', async () => {
    const {rerender, user} = render(<Filters disabled={false} />, {
      wrapper: createWrapper(),
    });

    await user.click(screen.getByRole('combobox', {name: 'Filter options'}));
    expect(
      screen.queryByRole('option', {name: /custom filter/i}),
    ).not.toBeInTheDocument();

    storeStateLocally('customFilters', {
      custom: {
        status: 'completed',
        assignee: 'me',
        bpmnProcess: 'process-1',
      },
    });

    rerender(<Filters disabled={false} />);

    expect(
      await screen.findByRole('option', {name: /custom filter/i}),
    ).toBeInTheDocument();
  });

  it('should write custom filter to the URL except variables', async () => {
    storeStateLocally('customFilters', {
      custom: {
        status: 'completed',
        assignee: 'me',
        bpmnProcess: 'process-1',
        variables: [
          {
            name: 'variable-1',
            value: 'value-1',
          },
        ],
      },
    });
    const {user} = render(<Filters disabled={false} />, {
      wrapper: createWrapper(),
    });

    await user.click(screen.getByRole('combobox', {name: 'Filter options'}));
    await user.click(screen.getByRole('option', {name: /custom filter/i}));

    expect(screen.getByTestId('search')).toHaveTextContent(
      '?filter=custom&sortBy=creation&assigned=true&assignee=&state=COMPLETED&processDefinitionKey=process-1',
    );
  });

  it('should apply new custom filters', async () => {
    const {user} = render(<Filters disabled={false} />, {
      wrapper: createWrapper(),
    });

    await user.click(screen.getByRole('button', {name: /custom filter/i}));
    await user.click(screen.getByRole('radio', {name: /me/i}));
    await user.click(screen.getByRole('button', {name: /apply/i}));

    expect(screen.getByTestId('search')).toHaveTextContent(
      '?filter=custom&sortBy=creation&assigned=true&assignee=demo',
    );
  });
});

/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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

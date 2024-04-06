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

import {render, screen, fireEvent, waitFor} from 'modules/testing-library';
import {MemoryRouter} from 'react-router-dom';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {generateTask} from 'modules/mock-schema/mocks/tasks';
import {Component} from './index';
import {http, HttpResponse} from 'msw';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import * as userMocks from 'modules/mock-schema/mocks/current-user';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/getMockQueryClient';
import {LocationLog} from 'modules/utils/LocationLog';

vi.mock('modules/stores/autoSelectFirstTask', () => ({
  autoSelectNextTaskStore: {
    enabled: true,
    toggle: vi.fn(),
  },
}));

const FIRST_PAGE = Array.from({length: 50}).map((_, index) =>
  generateTask(`${index}`),
);
const SECOND_PAGE = Array.from({length: 50}).map((_, index) =>
  generateTask(`${index + 50}`),
);

function getWrapper(
  initialEntries: React.ComponentProps<
    typeof MemoryRouter
  >['initialEntries'] = ['/'],
) {
  const mockClient = getMockQueryClient();

  type Props = {
    children?: React.ReactNode;
  };

  const Wrapper: React.FC<Props> = ({children}) => {
    return (
      <QueryClientProvider client={mockClient}>
        <MemoryRouter initialEntries={initialEntries}>
          <MockThemeProvider>
            {children}
            <LocationLog />
          </MockThemeProvider>
        </MemoryRouter>
      </QueryClientProvider>
    );
  };

  return Wrapper;
}

describe('<Tasks />', () => {
  it('should load more tasks', async () => {
    nodeMockServer.use(
      http.get(
        '/v1/internal/users/current',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
      http.post<never, {searchAfter: [string, string]}>(
        '/v1/tasks/search',
        async ({request}) => {
          const {searchAfter} = await request.json();
          if (searchAfter === undefined) {
            return HttpResponse.json(FIRST_PAGE);
          }

          return HttpResponse.json(SECOND_PAGE);
        },
      ),
    );

    render(<Component />, {
      wrapper: getWrapper(),
    });

    await waitFor(() =>
      expect(screen.getByTitle('All open tasks')).toBeDisabled(),
    );

    expect(await screen.findByText('TASK 0')).toBeInTheDocument();
    expect(screen.getByText('TASK 49')).toBeInTheDocument();
    expect(screen.getAllByRole('article')).toHaveLength(50);

    fireEvent.scroll(screen.getByTestId('scrollable-list'), {
      target: {scrollY: 100},
    });

    expect(screen.getByText('TASK 0')).toBeInTheDocument();
    expect(screen.getByText('TASK 49')).toBeInTheDocument();
    expect(await screen.findByText('TASK 50')).toBeInTheDocument();
    expect(screen.getByText('TASK 99')).toBeInTheDocument();
    expect(screen.getAllByRole('article')).toHaveLength(100);
  });

  it('should use tasklist api raw filters', async () => {
    nodeMockServer.use(
      http.get('/v1/internal/users/current', () => {
        return HttpResponse.json(userMocks.currentUser);
      }),
      http.post<never, {candidateUser: string; foo: unknown}>(
        '/v1/tasks/search',
        async ({request}) => {
          const {candidateUser, foo} = await request.json();

          if (candidateUser === 'demo' && foo === undefined) {
            return HttpResponse.json(FIRST_PAGE);
          }

          return HttpResponse.error();
        },
      ),
    );

    render(<Component />, {
      wrapper: getWrapper(['/?candidateUser=demo&foo=bar']),
    });

    expect(await screen.findByText('TASK 0')).toBeInTheDocument();
    expect(screen.queryByText('No tasks found')).not.toBeInTheDocument();
  });

  it('should select the first task when auto-select is enabled and tasks are in the list', async () => {
    nodeMockServer.use(
      http.get('/v1/internal/users/current', () => {
        return HttpResponse.json(userMocks.currentUser);
      }),
      http.post<never, {candidateUser: string; foo: unknown}>(
        '/v1/tasks/search',
        async () => {
          return HttpResponse.json(FIRST_PAGE);
        },
      ),
    );

    const {user} = render(<Component />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByTestId('pathname')).not.toHaveTextContent('/0');

    const toggle = screen.getByTestId('toggle-auto-select-task');
    expect(toggle).toBeInTheDocument();
    await user.click(toggle);

    await waitFor(() =>
      expect(screen.getByTestId('pathname')).toHaveTextContent('/0'),
    );
  });

  it('should go to the inital page when auto-select is enabled and no tasks are available', async () => {
    nodeMockServer.use(
      http.get('/v1/internal/users/current', () => {
        return HttpResponse.json(userMocks.currentUser);
      }),
      http.post<never, {candidateUser: string; foo: unknown}>(
        '/v1/tasks/search',
        async () => {
          return HttpResponse.json([]);
        },
      ),
    );

    const {user} = render(<Component />, {
      wrapper: getWrapper(['/0']),
    });

    expect(screen.getByTestId('pathname')).toHaveTextContent('/0');

    const toggle = screen.getByTestId('toggle-auto-select-task');
    expect(toggle).toBeInTheDocument();
    await user.click(toggle);

    await waitFor(() =>
      expect(screen.getByTestId('pathname')).toHaveTextContent('/'),
    );
  });
});

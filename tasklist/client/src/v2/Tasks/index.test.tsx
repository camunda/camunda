/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  render,
  screen,
  fireEvent,
  waitFor,
} from 'common/testing/testing-library';
import {MemoryRouter} from 'react-router-dom';
import {Component} from './index';
import {http, HttpResponse} from 'msw';
import {nodeMockServer} from 'common/testing/nodeMockServer';
import * as userMocks from 'common/mocks/current-user';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'common/testing/getMockQueryClient';
import {LocationLog} from 'common/testing/LocationLog';
import {unassignedTask} from 'v2/mocks/task';
import {getQueryTasksResponseMock} from 'v2/mocks/tasks';
import {
  endpoints,
  type QueryUserTasksRequestBody,
} from '@vzeta/camunda-api-zod-schemas/tasklist';

vi.mock('modules/stores/autoSelectFirstTask', () => ({
  autoSelectNextTaskStore: {
    enabled: false,
    enable: vi.fn(),
    disable: vi.fn(),
  },
}));

const FIRST_PAGE = Array.from({length: 50}).map((_, userTaskKey) =>
  unassignedTask({userTaskKey, name: `TASK ${userTaskKey}`}),
);
const SECOND_PAGE = Array.from({length: 50}).map((_, index) => {
  const userTaskKey = index + 50;

  return unassignedTask({
    userTaskKey: userTaskKey,
    name: `TASK ${userTaskKey}`,
  });
});

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
          {children}
          <LocationLog />
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
        '/v2/authentication/me',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
      http.post<never, QueryUserTasksRequestBody>(
        endpoints.queryUserTasks.getUrl(),
        async ({request}) => {
          const {page} = await request.json();

          switch (page?.from) {
            case 0:
              return HttpResponse.json(
                getQueryTasksResponseMock(FIRST_PAGE, 100),
              );
            case 50:
              return HttpResponse.json(
                getQueryTasksResponseMock(SECOND_PAGE, 100),
              );
            default:
              return HttpResponse.json(getQueryTasksResponseMock([], 0));
          }
        },
      ),
    );

    render(<Component />, {
      wrapper: getWrapper(),
    });

    await waitFor(() =>
      expect(screen.getByRole('button', {name: /sort tasks/i})).toBeDisabled(),
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
      http.get('/v2/authentication/me', () => {
        return HttpResponse.json(userMocks.currentUser);
      }),
      http.post<never, QueryUserTasksRequestBody & {foo: unknown}>(
        endpoints.queryUserTasks.getUrl(),
        async ({request}) => {
          const {filter, foo} = await request.json();

          if (filter?.candidateUser === 'demo' && foo === undefined) {
            return HttpResponse.json(getQueryTasksResponseMock(FIRST_PAGE, 50));
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

  it('should select the first open task when auto-select is enabled and tasks are in the list', async () => {
    nodeMockServer.use(
      http.get('/v2/authentication/me', () => {
        return HttpResponse.json(userMocks.currentUser);
      }),
      http.post(endpoints.queryUserTasks.getUrl(), async () => {
        return HttpResponse.json(
          getQueryTasksResponseMock(
            [
              {
                ...FIRST_PAGE[0],
                state: 'COMPLETED',
              },
              ...FIRST_PAGE.splice(1),
            ],
            100,
          ),
        );
      }),
    );

    const {user} = render(<Component />, {
      wrapper: getWrapper(),
    });

    expect(await screen.findByText('TASK 0')).toBeInTheDocument();
    expect(screen.getByTestId('pathname')).not.toHaveTextContent('/1');

    const toggle = screen.getByTestId('toggle-auto-select-task');
    expect(toggle).toBeInTheDocument();
    await user.click(toggle);

    await waitFor(() =>
      expect(screen.getByTestId('pathname')).toHaveTextContent('/1'),
    );
  });

  it('should go to the inital page when auto-select is enabled and no tasks are available', async () => {
    nodeMockServer.use(
      http.get('/v2/authentication/me', () => {
        return HttpResponse.json(userMocks.currentUser);
      }),
      http.post(endpoints.queryUserTasks.getUrl(), async () => {
        return HttpResponse.json(getQueryTasksResponseMock([], 0));
      }),
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

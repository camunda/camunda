/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Component} from './index';
import {
  render,
  screen,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {MemoryRouter} from 'react-router-dom';
import {storeStateLocally, clearStateLocally} from 'modules/utils/localStorage';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {http, HttpResponse} from 'msw';
import * as userMocks from 'modules/mock-schema/mocks/current-user';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/getMockQueryClient';
import {generateTask} from 'modules/mock-schema/mocks/tasks';

const getWrapper = () => {
  const mockClient = getMockQueryClient();

  const Wrapper: React.FC<{
    children?: React.ReactNode;
  }> = ({children}) => (
    <QueryClientProvider client={mockClient}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );

  return Wrapper;
};

describe('<EmptyPage isLoadingTasks={false} hasNoTasks={false} />', () => {
  afterEach(() => {
    clearStateLocally('hasCompletedTask');
  });

  it('should hide part of the empty message for new users', async () => {
    nodeMockServer.use(
      http.get(
        '/v1/internal/users/current',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
      http.post('/v1/tasks/search', async () => {
        return HttpResponse.json([]);
      }),
    );

    render(<Component />, {
      wrapper: getWrapper(),
    });

    expect(
      await screen.findByRole('heading', {
        name: 'Welcome to Tasklist',
      }),
    ).toBeInTheDocument();

    expect(
      screen.queryByText('Select a task to view its details.'),
    ).not.toBeInTheDocument();
  });

  it('should show an empty page message for new users', async () => {
    nodeMockServer.use(
      http.get(
        '/v1/internal/users/current',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
      http.post('/v1/tasks/search', async () => {
        return HttpResponse.json([generateTask('0')]);
      }),
    );

    render(<Component />, {
      wrapper: getWrapper(),
    });

    expect(
      await screen.findByRole('heading', {
        name: 'Welcome to Tasklist',
      }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('first-paragraph')).toHaveTextContent(
      // we have no space between `specify` and `through` because of the linebreak
      'Here you can perform user tasks you specifythrough your BPMN diagram and forms.',
    );
    expect(
      screen.getByText('Select a task to view its details.'),
    ).toBeInTheDocument();
    expect(screen.getByTestId('tutorial-paragraph')).toHaveTextContent(
      'Follow our tutorial to learn how to create tasks.',
    );
    expect(
      screen.getByRole('link', {name: 'learn how to create tasks.'}),
    ).toHaveAttribute(
      'href',
      'https://modeler.cloud.camunda.io/tutorial/quick-start-human-tasks',
    );
  });

  it('should show an empty page message for old users', async () => {
    nodeMockServer.use(
      http.get(
        '/v1/internal/users/current',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
      http.post('/v1/tasks/search', async () => {
        return HttpResponse.json([generateTask('0')]);
      }),
    );

    storeStateLocally('hasCompletedTask', true);

    render(<Component />, {
      wrapper: getWrapper(),
    });

    expect(
      await screen.findByRole('heading', {
        name: 'Pick a task to work on',
      }),
    ).toBeInTheDocument();
  });

  it('should not show an empty page message for old users', async () => {
    nodeMockServer.use(
      http.get(
        '/v1/internal/users/current',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
      http.post('/v1/tasks/search', async () => {
        return HttpResponse.json([]);
      }),
    );

    storeStateLocally('hasCompletedTask', true);

    const {container} = render(<Component />, {
      wrapper: getWrapper(),
    });

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('loading-state'),
    );

    expect(container).toBeEmptyDOMElement();
  });

  it('should show an empty page message for old readonly users', async () => {
    nodeMockServer.use(
      http.get(
        '/v1/internal/users/current',
        () => {
          return HttpResponse.json(userMocks.currentRestrictedUser);
        },
        {once: true},
      ),
      http.post('/v1/tasks/search', async () => {
        return HttpResponse.json([generateTask('0')]);
      }),
    );

    storeStateLocally('hasCompletedTask', true);

    render(<Component />, {
      wrapper: getWrapper(),
    });

    expect(
      await screen.findByRole('heading', {
        name: 'Pick a task to view details',
      }),
    ).toBeInTheDocument();
  });
});

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {EmptyPage} from './index';
import {render, screen} from 'modules/testing-library';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {ApolloProvider} from '@apollo/client';
import {client} from 'modules/apollo-client';
import {MemoryRouter} from 'react-router-dom';
import {storeStateLocally, clearStateLocally} from 'modules/utils/localStorage';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {graphql} from 'msw';
import {
  mockGetCurrentRestrictedUser,
  mockGetCurrentUser,
} from 'modules/queries/get-current-user';

const getWrapper = () => {
  const Wrapper: React.FC<{
    children?: React.ReactNode;
  }> = ({children}) => (
    <MockThemeProvider>
      <ApolloProvider client={client}>
        <MemoryRouter>{children}</MemoryRouter>
      </ApolloProvider>
    </MockThemeProvider>
  );

  return Wrapper;
};

describe('<EmptyPage isLoadingTasks={false} hasNoTasks={false} />', () => {
  afterEach(() => {
    clearStateLocally('hasCompletedTask');
  });

  it('should hide part of the empty message for new users', async () => {
    nodeMockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser));
      }),
    );

    render(<EmptyPage isLoadingTasks={false} hasNoTasks />, {
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
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser));
      }),
    );

    render(<EmptyPage isLoadingTasks={false} hasNoTasks={false} />, {
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
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser));
      }),
    );

    storeStateLocally('hasCompletedTask', true);

    render(<EmptyPage isLoadingTasks={false} hasNoTasks={false} />, {
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
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser));
      }),
    );

    storeStateLocally('hasCompletedTask', true);

    const {container, rerender} = render(
      <EmptyPage isLoadingTasks hasNoTasks />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(screen.getByTestId('loading-state')).toBeInTheDocument();

    rerender(<EmptyPage isLoadingTasks={false} hasNoTasks />);

    expect(screen.queryByTestId('loading-state')).not.toBeInTheDocument();
    expect(container).toBeEmptyDOMElement();
  });

  it('should show an empty page message for old readonly users', async () => {
    nodeMockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentRestrictedUser));
      }),
    );

    storeStateLocally('hasCompletedTask', true);

    render(<EmptyPage isLoadingTasks={false} hasNoTasks={false} />, {
      wrapper: getWrapper(),
    });

    expect(
      await screen.findByRole('heading', {
        name: 'Pick a task to view details',
      }),
    ).toBeInTheDocument();
  });
});

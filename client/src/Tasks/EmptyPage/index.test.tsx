/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {graphql} from 'msw';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {
  mockGetAllOpenTasks,
  mockGetEmptyTasks,
} from 'modules/queries/get-tasks';
import {EmptyPage} from './index';
import {
  render,
  screen,
  waitForElementToBeRemoved,
} from '@testing-library/react';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {ApolloProvider} from '@apollo/client';
import {client} from 'modules/apollo-client';
import {MemoryRouter} from 'react-router-dom';
import {storeStateLocally, clearStateLocally} from 'modules/utils/localStorage';

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

describe('<EmptyPage />', () => {
  afterEach(() => {
    clearStateLocally('hasCompletedTask');
  });

  it('should hide part of the empty message for new users', async () => {
    nodeMockServer.use(
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(ctx.data(mockGetEmptyTasks.result.data));
      }),
    );

    render(<EmptyPage />, {
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
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(ctx.data(mockGetAllOpenTasks().result.data));
      }),
    );

    render(<EmptyPage />, {
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
    storeStateLocally('hasCompletedTask', true);
    nodeMockServer.use(
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(ctx.data(mockGetAllOpenTasks().result.data));
      }),
    );

    render(<EmptyPage />, {
      wrapper: getWrapper(),
    });

    expect(
      await screen.findByRole('heading', {
        name: 'Pick a task to work on.',
      }),
    ).toBeInTheDocument();
  });

  it('should not show an empty page message for old users', async () => {
    storeStateLocally('hasCompletedTask', true);
    nodeMockServer.use(
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(ctx.data(mockGetEmptyTasks.result.data));
      }),
    );

    const {container} = render(<EmptyPage />, {
      wrapper: getWrapper(),
    });

    await waitForElementToBeRemoved(() => screen.getByTestId('loading-state'));

    expect(container).toBeEmptyDOMElement();
  });
});

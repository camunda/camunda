/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  render,
  screen,
  waitForElementToBeRemoved,
  fireEvent,
} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {generateTask} from 'modules/mock-schema/mocks/tasks';
import {mockGetCurrentUser} from 'modules/queries/get-current-user';
import {Tasks} from './index';
import {ApolloProvider} from '@apollo/client';
import {createApolloClient} from 'modules/apollo-client';
import {graphql} from 'msw';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';

const mockApolloClient = createApolloClient({maxTasksDisplayed: 5});

type Props = {
  children?: React.ReactNode;
};

const Wrapper: React.FC<Props> = ({children}) => {
  return (
    <ApolloProvider client={mockApolloClient}>
      <MemoryRouter initialEntries={['/']}>
        <MockThemeProvider>{children}</MockThemeProvider>
      </MemoryRouter>
    </ApolloProvider>
  );
};

describe('<Layout />', () => {
  it('should load more tasks', async () => {
    nodeMockServer.use(
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(
          ctx.data({
            tasks: [generateTask('1'), generateTask('2')],
          }),
        );
      }),
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser));
      }),
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(
          ctx.data({
            tasks: [generateTask('3'), generateTask('4')],
          }),
        );
      }),
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(
          ctx.data({
            tasks: [generateTask('5'), generateTask('6')],
          }),
        );
      }),
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(
          ctx.data({
            tasks: [generateTask('7'), generateTask('8')],
          }),
        );
      }),
    );

    render(<Tasks />, {
      wrapper: Wrapper,
    });

    expect(screen.getByTitle('All open')).toBeDisabled();

    await waitForElementToBeRemoved(screen.getByTestId('tasks-skeleton'));

    fireEvent.scroll(screen.getByTestId('scrollable-list'), {
      target: {scrollY: 100},
    });

    expect(await screen.findByText('TASK 3')).toBeInTheDocument();
    expect(screen.getByText('TASK 4')).toBeInTheDocument();

    fireEvent.scroll(screen.getByTestId('scrollable-list'), {
      target: {scrollY: 100},
    });

    expect(await screen.findByText('TASK 5')).toBeInTheDocument();
    expect(screen.queryByText('TASK 1')).not.toBeInTheDocument();
    expect(screen.getByText('TASK 2')).toBeInTheDocument();
    expect(screen.getByText('TASK 3')).toBeInTheDocument();
    expect(screen.getByText('TASK 4')).toBeInTheDocument();
    expect(screen.getByText('TASK 6')).toBeInTheDocument();

    fireEvent.scroll(screen.getByTestId('scrollable-list'), {
      target: {scrollY: 100},
    });

    expect(await screen.findByText('TASK 7')).toBeInTheDocument();
    expect(screen.queryByText('TASK 1')).not.toBeInTheDocument();
    expect(screen.queryByText('TASK 2')).not.toBeInTheDocument();
    expect(screen.queryByText('TASK 3')).not.toBeInTheDocument();
    expect(screen.getByText('TASK 4')).toBeInTheDocument();
    expect(screen.getByText('TASK 5')).toBeInTheDocument();
    expect(screen.getByText('TASK 6')).toBeInTheDocument();
    expect(screen.getByText('TASK 8')).toBeInTheDocument();
  });
});

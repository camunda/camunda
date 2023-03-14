/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen, fireEvent} from 'modules/testing-library';
import {Task} from './index';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {MemoryRouter} from 'react-router-dom';
import {currentUser} from 'modules/mock-schema/mocks/current-user';
import {LocationLog} from 'modules/utils/LocationLog';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {graphql} from 'msw';
import {mockGetCurrentUser} from 'modules/queries/get-current-user';
import {client} from 'modules/apollo-client';
import {ApolloProvider} from '@apollo/client';

jest.mock('modules/featureFlags');

const createWrapper = (
  initialEntries: React.ComponentProps<
    typeof MemoryRouter
  >['initialEntries'] = ['/'],
) => {
  const Wrapper: React.FC<{
    children?: React.ReactNode;
  }> = ({children}) => (
    <ApolloProvider client={client}>
      <MockThemeProvider>
        <MemoryRouter initialEntries={initialEntries}>
          {children}
          <LocationLog />
        </MemoryRouter>
      </MockThemeProvider>
    </ApolloProvider>
  );

  return Wrapper;
};

describe('<Task />', () => {
  beforeEach(() => {
    nodeMockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res(ctx.data(mockGetCurrentUser));
      }),
    );
  });

  it('should render task', async () => {
    render(
      <Task
        taskId="1"
        name="name"
        processName="processName"
        creationTime="2020-05-29T14:00:00.000Z"
        assignee={currentUser.userId}
        followUpDate={null}
        dueDate={null}
      />,
      {
        wrapper: createWrapper(),
      },
    );

    expect(screen.getByText('name')).toBeInTheDocument();
    expect(screen.getByText('processName')).toBeInTheDocument();
    expect(
      screen.getByTitle('Created at 29 May 2020 - 02:00 PM'),
    ).toBeInTheDocument();
    expect(await screen.findByText('Assigned to me')).toBeInTheDocument();
  });

  it('should handle unassigned tasks', () => {
    render(
      <Task
        taskId="1"
        name="name"
        processName="processName"
        creationTime="2020-05-29T14:00:00.000Z"
        assignee={null}
        followUpDate={null}
        dueDate={null}
      />,
      {
        wrapper: createWrapper(),
      },
    );

    expect(screen.getByText('Unassigned')).toBeInTheDocument();
  });

  it('should render creation time as empty value if given date is invalid', () => {
    render(
      <Task
        taskId="1"
        name="name"
        processName="processName"
        creationTime="invalid date"
        assignee={currentUser.userId}
        followUpDate={null}
        dueDate={null}
      />,
      {
        wrapper: createWrapper(),
      },
    );

    expect(screen.getByTestId('creation-time')).toBeEmptyDOMElement();
  });

  it('should navigate to task detail on click', () => {
    render(
      <Task
        taskId="1"
        name="name"
        processName="processName"
        creationTime="2020-05-29T14:00:00.000Z"
        assignee={currentUser.userId}
        followUpDate={null}
        dueDate={null}
      />,
      {
        wrapper: createWrapper(),
      },
    );

    fireEvent.click(screen.getByText('processName'));
    expect(screen.getByTestId('pathname')).toHaveTextContent('/1');
  });

  it('should preserve search params', () => {
    render(
      <Task
        taskId="1"
        name="name"
        processName="processName"
        creationTime="2020-05-29T14:00:00.000Z"
        assignee={currentUser.userId}
        followUpDate={null}
        dueDate={null}
      />,
      {
        wrapper: createWrapper(['/?filter=all-open']),
      },
    );

    fireEvent.click(screen.getByText('processName'));

    expect(screen.getByTestId('pathname')).toHaveTextContent('/1');
    expect(screen.getByTestId('search')).toHaveTextContent('filter=all-open');
  });

  it('should render a task with due date', async () => {
    render(
      <Task
        taskId="1"
        name="name"
        processName="processName"
        creationTime="2020-05-29T14:00:00.000Z"
        assignee={currentUser.userId}
        followUpDate={null}
        dueDate="2021-05-29T14:00:00.000Z"
      />,
      {
        wrapper: createWrapper(),
      },
    );

    expect(screen.getByTitle('Due at 29 May 2021')).toBeInTheDocument();
  });

  it('should render a task with due date when filtered', async () => {
    render(
      <Task
        taskId="1"
        name="name"
        processName="processName"
        creationTime="2020-05-29T14:00:00.000Z"
        assignee={currentUser.userId}
        followUpDate="2021-05-29T14:00:00.000Z"
        dueDate="2021-05-29T14:00:00.000Z"
      />,
      {
        wrapper: createWrapper(['/?sortBy=due']),
      },
    );

    expect(screen.getByTitle('Due at 29 May 2021')).toBeInTheDocument();
    expect(screen.queryByText('Follow-up at')).not.toBeInTheDocument();
  });

  it('should render a task with follow-up date when filtered', async () => {
    render(
      <Task
        taskId="1"
        name="name"
        processName="processName"
        creationTime="2020-05-29T14:00:00.000Z"
        assignee={currentUser.userId}
        followUpDate="2021-05-29T14:00:00.000Z"
        dueDate="2021-05-29T14:00:00.000Z"
      />,
      {
        wrapper: createWrapper(['/?sortBy=follow-up']),
      },
    );

    expect(screen.getByTitle('Follow-up at 29 May 2021')).toBeInTheDocument();
    expect(screen.queryByText('Due at')).not.toBeInTheDocument();
  });
});

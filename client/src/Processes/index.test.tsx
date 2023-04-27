/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ApolloProvider} from '@apollo/client';
import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {client} from 'modules/apollo-client';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {
  createMockProcess,
  GetProcesses,
  GetProcessesVariables,
} from 'modules/queries/get-processes';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {graphql} from 'msw';
import {MemoryRouter} from 'react-router-dom';
import {Processes} from './index';
import {notificationsStore} from 'modules/stores/notifications';

jest.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: jest.fn(() => () => {}),
  },
}));

const mockedNotificationsStore = notificationsStore as jest.Mocked<
  typeof notificationsStore
>;

type Props = {
  children?: React.ReactNode;
};

const Wrapper: React.FC<Props> = ({children}) => {
  return (
    <ApolloProvider client={client}>
      <MemoryRouter initialEntries={['/']}>
        <MockThemeProvider>{children}</MockThemeProvider>
      </MemoryRouter>
    </ApolloProvider>
  );
};

describe('Processes', () => {
  afterEach(() => {
    mockedNotificationsStore.displayNotification.mockClear();
    process.env.REACT_APP_VERSION = '1.2.3';
  });

  it('should render an empty state message', async () => {
    window.localStorage.setItem('hasConsentedToStartProcess', 'true');
    nodeMockServer.use(
      graphql.query<GetProcesses, GetProcessesVariables>(
        'GetProcesses',
        (_, res, ctx) => {
          return res(
            ctx.data({
              processes: [],
            }),
          );
        },
      ),
    );

    render(<Processes />, {
      wrapper: Wrapper,
    });

    expect(screen.getByPlaceholderText('Search processes')).toBeDisabled();
    expect(
      screen.queryByText(
        'Start processes on demand directly from your tasklist.',
      ),
    ).not.toBeInTheDocument();

    await waitForElementToBeRemoved(() =>
      screen.getAllByTestId('process-skeleton'),
    );

    expect(screen.getByPlaceholderText('Search processes')).toBeEnabled();
    expect(
      screen.getByRole('heading', {
        name: 'No published processes yet',
      }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('empty-message')).toHaveTextContent(
      'Contact your process administrator to publish processes or learn how to publish processes here',
    );
    expect(screen.getByRole('link', {name: 'here'})).toHaveAttribute(
      'href',
      'https://docs.camunda.io/',
    );
  });

  it('should render a list of processes', async () => {
    window.localStorage.setItem('hasConsentedToStartProcess', 'true');
    nodeMockServer.use(
      graphql.query<GetProcesses, GetProcessesVariables>(
        'GetProcesses',
        (_, res, ctx) => {
          return res(
            ctx.data({
              processes: [
                createMockProcess('process-0'),
                createMockProcess('process-1'),
              ],
            }),
          );
        },
      ),
    );

    render(<Processes />, {
      wrapper: Wrapper,
    });

    await waitForElementToBeRemoved(() =>
      screen.getAllByTestId('process-skeleton'),
    );

    expect(screen.getAllByTestId('process-tile')).toHaveLength(2);
  });

  it('should show an error toast when the query fails', async () => {
    window.localStorage.setItem('hasConsentedToStartProcess', 'true');
    nodeMockServer.use(
      graphql.query<GetProcesses, GetProcessesVariables>(
        'GetProcesses',
        (_, res, ctx) => {
          return res.once(
            ctx.errors([
              {
                message: 'Something went wrong',
              },
            ]),
          );
        },
      ),
    );

    render(<Processes />, {
      wrapper: Wrapper,
    });

    await waitFor(() =>
      expect(mockedNotificationsStore.displayNotification).toBeCalledWith({
        isDismissable: false,
        kind: 'error',
        title: 'Processes could not be fetched',
      }),
    );
  });
});

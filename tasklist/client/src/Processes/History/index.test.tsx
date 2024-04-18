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

import {
  render,
  screen,
  waitForElementToBeRemoved,
} from '@testing-library/react';
import {History} from './index';
import {MemoryRouter} from 'react-router-dom';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {http, HttpResponse} from 'msw';
import * as userMocks from 'modules/mock-schema/mocks/current-user';
import * as processInstancesMocks from 'modules/mock-schema/mocks/process-instances';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/getMockQueryClient';

const getWrapper = () => {
  const mockClient = getMockQueryClient();

  type Props = {
    children: React.ReactNode;
  };

  const Wrapper: React.FC<Props> = ({children}) => {
    return (
      <QueryClientProvider client={mockClient}>
        <MemoryRouter>{children}</MemoryRouter>
      </QueryClientProvider>
    );
  };

  return Wrapper;
};

describe('<History />', () => {
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

  it('should fetch process instances', async () => {
    nodeMockServer.use(
      http.post(
        '/internal/users/:userId/process-instances',
        () => {
          return HttpResponse.json(processInstancesMocks.processInstances);
        },
        {once: true},
      ),
    );

    render(<History />, {
      wrapper: getWrapper(),
    });

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('history-skeleton'),
    );

    const [{process, id}] = processInstancesMocks.processInstances;

    expect(screen.getAllByText(process.bpmnProcessId)).toHaveLength(2);
    expect(screen.getAllByText(process.name!)).toHaveLength(2);
    expect(screen.getByText(id)).toBeInTheDocument();
    expect(
      screen.getByText('01 Jan 2021 - 12:00 AM - Completed'),
    ).toBeInTheDocument();
    expect(screen.getByTestId('completed-icon')).toBeInTheDocument();
    expect(screen.getByTestId('active-icon')).toBeInTheDocument();
    expect(screen.getByTestId('incident-icon')).toBeInTheDocument();
    expect(screen.getByTestId('terminated-icon')).toBeInTheDocument();
  });

  it('should show error message when fetching process instances fails', async () => {
    nodeMockServer.use(
      http.post(
        '/internal/users/:userId/process-instances',
        () => {
          return new HttpResponse(null, {status: 500});
        },
        {once: true},
      ),
    );

    render(<History />, {
      wrapper: getWrapper(),
    });

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('history-skeleton'),
    );

    expect(
      screen.getByText('Oops! Something went wrong while fetching the history'),
    ).toBeInTheDocument();
    expect(
      screen.getByText('Please check your internet connection and try again.'),
    ).toBeInTheDocument();
  });

  it('should show a message when no process instances are found', async () => {
    nodeMockServer.use(
      http.post(
        '/internal/users/:userId/process-instances',
        () => {
          return HttpResponse.json([]);
        },
        {once: true},
      ),
    );

    render(<History />, {
      wrapper: getWrapper(),
    });

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('history-skeleton'),
    );

    expect(screen.getByText('No history entries found')).toBeInTheDocument();
    expect(
      screen.getByText(
        'There is no history to display. Start a new process to see it here.',
      ),
    ).toBeInTheDocument();
  });
});

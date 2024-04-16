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

import {Component} from './index';
import {
  render,
  screen,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {MockThemeProvider} from 'modules/theme/MockProvider';
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
      <MockThemeProvider>
        <MemoryRouter>{children}</MemoryRouter>
      </MockThemeProvider>
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

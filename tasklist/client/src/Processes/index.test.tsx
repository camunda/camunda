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

import {Mocked} from 'vitest';
import {
  fireEvent,
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
  within,
} from 'modules/testing-library';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {createMockProcess} from 'modules/queries/useProcesses';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {http, HttpResponse} from 'msw';
import {MemoryRouter} from 'react-router-dom';
import {Component} from './index';
import {notificationsStore} from 'modules/stores/notifications';
import * as formMocks from 'modules/mock-schema/mocks/form';
import * as userMocks from 'modules/mock-schema/mocks/current-user';
import {DEFAULT_MOCK_CLIENT_CONFIG} from 'modules/mocks/window';
import {LocationLog} from 'modules/utils/LocationLog';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/getMockQueryClient';
import {pages} from 'modules/routing';

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

const mockedNotificationsStore = notificationsStore as Mocked<
  typeof notificationsStore
>;

const getWrapper = (initialEntries = ['/']) => {
  const mockClient = getMockQueryClient();

  type Props = {
    children?: React.ReactNode;
  };

  const Wrapper: React.FC<Props> = ({children}) => {
    return (
      <QueryClientProvider client={mockClient}>
        <MemoryRouter initialEntries={initialEntries}>
          <MockThemeProvider>{children}</MockThemeProvider>
          <LocationLog />
        </MemoryRouter>
      </QueryClientProvider>
    );
  };

  return Wrapper;
};

describe('Processes', () => {
  afterEach(() => {
    mockedNotificationsStore.displayNotification.mockClear();
    import.meta.env.VITE_VERSION = '1.2.3';
  });

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

  it('should render an empty state message', async () => {
    window.localStorage.setItem('hasConsentedToStartProcess', 'true');
    nodeMockServer.use(
      http.get(
        '/v1/internal/processes',
        () => {
          return HttpResponse.json([]);
        },
        {once: true},
      ),
    );

    render(<Component />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByPlaceholderText('Search processes')).toBeDisabled();
    expect(
      screen.queryByText(
        'Start processes on demand directly from your tasklist.',
      ),
    ).not.toBeInTheDocument();

    await waitForElementToBeRemoved(() =>
      screen.queryAllByTestId('process-skeleton'),
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
      'https://docs.camunda.io/docs/components/modeler/web-modeler/run-or-publish-your-process/#publishing-a-process',
    );
  });

  it('should render a list of processes', async () => {
    window.localStorage.setItem('hasConsentedToStartProcess', 'true');
    nodeMockServer.use(
      http.get('/v1/internal/processes', () => {
        return HttpResponse.json([
          createMockProcess('process-0'),
          createMockProcess('process-1'),
        ]);
      }),
    );

    render(<Component />, {
      wrapper: getWrapper(),
    });

    await waitForElementToBeRemoved(() =>
      screen.queryAllByTestId('process-skeleton'),
    );

    expect(screen.getAllByTestId('process-tile')).toHaveLength(2);
  });

  it('should open a dialog with the start form', async () => {
    window.localStorage.setItem('hasConsentedToStartProcess', 'true');
    const mockProcess = createMockProcess('process-0');
    nodeMockServer.use(
      http.get('/v1/internal/processes', () => {
        return HttpResponse.json([mockProcess]);
      }),
      http.get('/v1/forms/:formId', () => {
        return HttpResponse.json(formMocks.form);
      }),
    );

    render(<Component />, {
      wrapper: getWrapper(),
    });

    await waitForElementToBeRemoved(() =>
      screen.queryAllByTestId('process-skeleton'),
    );

    expect(screen.getAllByTestId('process-tile')).toHaveLength(1);

    fireEvent.click(
      within(screen.getByTestId('process-tile')).getByRole('button', {
        name: 'Start process',
      }),
    );

    expect(
      screen.getByRole('dialog', {
        name: `Start process ${mockProcess.name}`,
      }),
    ).toBeInTheDocument();
    expect(await screen.findByText('A sample text')).toBeInTheDocument();
    expect(screen.getByTestId('pathname')).toHaveTextContent(
      pages.internalStartProcessFromForm(mockProcess.bpmnProcessId),
    );
  });

  it('should show an error toast when the query fails', async () => {
    window.localStorage.setItem('hasConsentedToStartProcess', 'true');
    nodeMockServer.use(
      http.get('/v1/internal/processes', () => {
        return HttpResponse.error();
      }),
    );

    render(<Component />, {
      wrapper: getWrapper(),
    });

    await waitFor(() =>
      expect(mockedNotificationsStore.displayNotification).toBeCalledWith({
        isDismissable: false,
        kind: 'error',
        title: 'Processes could not be fetched',
      }),
    );
  });

  it('should disable the start button', async () => {
    window.localStorage.setItem('hasConsentedToStartProcess', 'true');
    nodeMockServer.use(
      http.get('/v1/internal/processes', () => {
        return HttpResponse.json([createMockProcess('process-0')]);
      }),
      http.get(
        '/v1/internal/users/current',
        () => {
          return HttpResponse.json(userMocks.currentRestrictedUser);
        },
        {once: true},
      ),
    );

    render(<Component />, {
      wrapper: getWrapper(),
    });

    await waitForElementToBeRemoved(() =>
      screen.queryAllByTestId('process-skeleton'),
    );

    expect(
      within(screen.getByTestId('process-tile')).getByRole('button', {
        name: 'Start process',
      }),
    ).toBeDisabled();
  });

  it('should show the filter dropdown', async () => {
    window.localStorage.setItem('hasConsentedToStartProcess', 'true');
    nodeMockServer.use(
      http.get('/v1/internal/processes', () => {
        return HttpResponse.json([]);
      }),
    );

    render(<Component />, {
      wrapper: getWrapper(),
    });

    await waitForElementToBeRemoved(() =>
      screen.queryAllByTestId('process-skeleton'),
    );

    expect(screen.getByTitle('All Processes')).toBeVisible();
  });

  it('should render a tenant dropdown', async () => {
    window.localStorage.setItem('hasConsentedToStartProcess', 'true');
    window.clientConfig = {
      ...DEFAULT_MOCK_CLIENT_CONFIG,
      isMultiTenancyEnabled: true,
    };
    nodeMockServer.use(
      http.get('/v1/internal/processes', () => {
        return HttpResponse.json([]);
      }),
      http.get(
        '/v1/internal/users/current',
        () => {
          return HttpResponse.json(userMocks.currentUserWithTenants);
        },
        {once: true},
      ),
    );

    render(<Component />, {
      wrapper: getWrapper(),
    });

    expect(
      await screen.findByRole('combobox', {name: 'Tenant'}),
    ).toBeInTheDocument();

    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    fireEvent.click(screen.getByRole('combobox', {name: 'Tenant'}));
    fireEvent.click(screen.getByRole('option', {name: 'Tenant B - tenantB'}));

    expect(screen.getByTestId('search').textContent).toContain(
      'tenantId=tenantB',
    );
  });

  it('should render a tenant dropdown with the current tenant selected', async () => {
    window.localStorage.setItem('hasConsentedToStartProcess', 'true');
    window.localStorage.setItem('tenantId', '"tenantA"');
    window.clientConfig = {
      ...DEFAULT_MOCK_CLIENT_CONFIG,
      isMultiTenancyEnabled: true,
    };
    nodeMockServer.use(
      http.get('/v1/internal/processes', () => {
        return HttpResponse.json([]);
      }),
      http.get(
        '/v1/internal/users/current',
        () => {
          return HttpResponse.json(userMocks.currentUserWithTenants);
        },
        {once: true},
      ),
    );

    render(<Component />, {
      wrapper: getWrapper(),
    });

    await waitForElementToBeRemoved(() =>
      screen.queryAllByTestId('process-skeleton'),
    );

    expect(screen.getByTestId('search').textContent).toContain(
      'tenantId=tenantA',
    );
    expect(
      within(screen.getByRole('combobox', {name: 'Tenant'})).getByText(
        'Tenant A - tenantA',
      ),
    ).toBeInTheDocument();
  });

  it('should not render dropdown with single tenant', async () => {
    window.localStorage.setItem('hasConsentedToStartProcess', 'true');
    window.clientConfig = {
      ...DEFAULT_MOCK_CLIENT_CONFIG,
      isMultiTenancyEnabled: true,
    };
    nodeMockServer.use(
      http.get('/v1/internal/processes', () => {
        return HttpResponse.json([]);
      }),
      http.get(
        '/v1/internal/users/current',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
    );

    render(<Component />, {
      wrapper: getWrapper(),
    });

    await waitForElementToBeRemoved(() =>
      screen.queryAllByTestId('process-skeleton'),
    );

    expect(
      screen.queryByRole('combobox', {name: 'Tenant'}),
    ).not.toBeInTheDocument();
  });

  it('should render the start form with the correct URL', async () => {
    window.localStorage.setItem('hasConsentedToStartProcess', 'true');
    const mockProcess = createMockProcess('process-0');
    nodeMockServer.use(
      http.get('/v1/internal/processes', () => {
        return HttpResponse.json([mockProcess]);
      }),
      http.get('/v1/forms/:bpmnProcessId', () => {
        return HttpResponse.json(formMocks.form);
      }),
    );

    render(<Component />, {
      wrapper: getWrapper([
        pages.internalStartProcessFromForm(mockProcess.bpmnProcessId),
      ]),
    });

    await waitForElementToBeRemoved(() =>
      screen.queryAllByTestId('process-skeleton'),
    );

    expect(
      screen.getByRole('dialog', {
        name: `Start process ${mockProcess.name}`,
      }),
    ).toBeInTheDocument();
    expect(await screen.findByText('A sample text')).toBeInTheDocument();
    expect(screen.getByTestId('pathname')).toHaveTextContent(
      pages.internalStartProcessFromForm(mockProcess.bpmnProcessId),
    );
  });

  it('should show a toast message when opened start form process does not exist', async () => {
    window.localStorage.setItem('hasConsentedToStartProcess', 'true');
    const wrongBpmnProcessId = 'wrong-bpmn-process-id';
    nodeMockServer.use(
      http.get('/v1/internal/processes', () => {
        return HttpResponse.json([createMockProcess('process-0')]);
      }),
    );

    render(<Component />, {
      wrapper: getWrapper([
        pages.internalStartProcessFromForm(wrongBpmnProcessId),
      ]),
    });

    await waitForElementToBeRemoved(() =>
      screen.queryAllByTestId('process-skeleton'),
    );

    await waitFor(() =>
      expect(mockedNotificationsStore.displayNotification).toBeCalledWith({
        isDismissable: false,
        kind: 'error',
        title: `Process ${wrongBpmnProcessId} does not exist or has no start form`,
      }),
    );
  });
});

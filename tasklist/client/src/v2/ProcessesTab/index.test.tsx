/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Mocked} from 'vitest';
import {
  fireEvent,
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
  within,
} from 'common/testing/testing-library';
import {nodeMockServer} from 'common/testing/nodeMockServer';
import {http, HttpResponse, type PathParams} from 'msw';
import {MemoryRouter} from 'react-router-dom';
import {Component} from './index';
import {notificationsStore} from 'common/notifications/notifications.store';
import * as formMocks from 'v2/mocks/form';
import * as userMocks from 'common/mocks/current-user';
import {
  getProcessDefinitionMock,
  getQueryProcessDefinitionsResponseMock,
} from 'v2/mocks/processDefinitions';
import {LocationLog} from 'common/testing/LocationLog';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'common/testing/getMockQueryClient';
import {pages} from 'common/routing';
import {getClientConfig} from 'common/config/getClientConfig';
import type {QueryProcessDefinitionsRequestBody} from '@vzeta/camunda-api-zod-schemas/8.8';

vi.mock('common/notifications/notifications.store', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

vi.mock('common/config/getClientConfig', async (importOriginal) => {
  const actual =
    await importOriginal<typeof import('common/config/getClientConfig')>();
  return {
    getClientConfig: vi.fn().mockImplementation(actual.getClientConfig),
  };
});

const {getClientConfig: actualGetClientConfig} = await vi.importActual<
  typeof import('common/config/getClientConfig')
>('common/config/getClientConfig');
const mockGetClientConfig = vi.mocked(getClientConfig);

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
          {children}
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
    mockGetClientConfig.mockReturnValue(actualGetClientConfig());
    nodeMockServer.use(
      http.get(
        '/v2/authentication/me',
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
      http.post(
        '/v2/process-definitions/search',
        () => {
          return HttpResponse.json(getQueryProcessDefinitionsResponseMock([]));
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
      http.post('/v2/process-definitions/search', () => {
        return HttpResponse.json(
          getQueryProcessDefinitionsResponseMock([
            getProcessDefinitionMock(),
            getProcessDefinitionMock(),
          ]),
        );
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
    const mockProcess = getProcessDefinitionMock({hasStartForm: true});
    nodeMockServer.use(
      http.post('/v2/process-definitions/search', () => {
        return HttpResponse.json(
          getQueryProcessDefinitionsResponseMock([mockProcess]),
        );
      }),
      http.get('/v2/process-definitions/:processDefinitionKey/form', () => {
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
      pages.internalStartProcessFromForm(mockProcess.processDefinitionKey),
    );
  });

  it('should show an error toast when the query fails', async () => {
    window.localStorage.setItem('hasConsentedToStartProcess', 'true');
    nodeMockServer.use(
      http.post('/v2/process-definitions/search', () => {
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

  it('should show the filter dropdown', async () => {
    window.localStorage.setItem('hasConsentedToStartProcess', 'true');
    nodeMockServer.use(
      http.post('/v2/process-definitions/search', () => {
        return HttpResponse.json(getQueryProcessDefinitionsResponseMock([]));
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
    mockGetClientConfig.mockReturnValue({
      ...actualGetClientConfig(),
      isMultiTenancyEnabled: true,
    });
    nodeMockServer.use(
      http.post('/v2/process-definitions/search', () => {
        return HttpResponse.json(getQueryProcessDefinitionsResponseMock([]));
      }),
      http.get(
        '/v2/authentication/me',
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
    mockGetClientConfig.mockReturnValue({
      ...actualGetClientConfig(),
      isMultiTenancyEnabled: true,
    });
    nodeMockServer.use(
      http.post('/v2/process-definitions/search', () => {
        return HttpResponse.json(getQueryProcessDefinitionsResponseMock([]));
      }),
      http.get(
        '/v2/authentication/me',
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
    mockGetClientConfig.mockReturnValue({
      ...actualGetClientConfig(),
      isMultiTenancyEnabled: true,
    });
    nodeMockServer.use(
      http.post('/v2/process-definitions/search', () => {
        return HttpResponse.json(getQueryProcessDefinitionsResponseMock([]));
      }),
      http.get(
        '/v2/authentication/me',
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
    const mockProcess = getProcessDefinitionMock();
    nodeMockServer.use(
      http.post('/v2/process-definitions/search', () => {
        return HttpResponse.json(
          getQueryProcessDefinitionsResponseMock([mockProcess]),
        );
      }),
      http.get('/v2/process-definitions/:processDefinitionKey/form', () => {
        return HttpResponse.json(formMocks.form);
      }),
    );

    render(<Component />, {
      wrapper: getWrapper([
        pages.internalStartProcessFromForm(mockProcess.processDefinitionKey),
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
      pages.internalStartProcessFromForm(mockProcess.processDefinitionKey),
    );
  });

  it('should show a toast message when opened start form process does not exist', async () => {
    window.localStorage.setItem('hasConsentedToStartProcess', 'true');
    const wrongBpmnProcessId = 'wrong-bpmn-process-id';
    nodeMockServer.use(
      http.post('/v2/process-definitions/search', () => {
        return HttpResponse.json(
          getQueryProcessDefinitionsResponseMock([getProcessDefinitionMock()]),
        );
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

  it('should handle pagination correctly', async () => {
    const processesPage1 = [
      getProcessDefinitionMock({
        name: 'Process A',
        processDefinitionId: 'procA',
      }),
      getProcessDefinitionMock({
        name: 'Process B',
        processDefinitionId: 'procB',
      }),
    ];
    const processesPage2 = [
      getProcessDefinitionMock({
        name: 'Process C',
        processDefinitionId: 'procC',
      }),
    ];
    const TOTAL_ITEMS = 24;

    nodeMockServer.use(
      http.post<PathParams, QueryProcessDefinitionsRequestBody>(
        '/v2/process-definitions/search',
        async ({request}) => {
          const body = await request.json();
          const after = body.page?.after;

          if (typeof after === 'string') {
            return HttpResponse.json(
              getQueryProcessDefinitionsResponseMock(
                processesPage2,
                TOTAL_ITEMS,
              ),
            );
          }

          return HttpResponse.json(
            getQueryProcessDefinitionsResponseMock(processesPage1, TOTAL_ITEMS),
          );
        },
      ),
    );

    const {user} = render(<Component />, {
      wrapper: getWrapper(),
    });

    await waitForElementToBeRemoved(() =>
      screen.queryAllByTestId('process-skeleton'),
    );

    expect(screen.getByText('Process A')).toBeVisible();
    expect(screen.getByText('Process B')).toBeVisible();
    expect(screen.queryByText('Process C')).not.toBeInTheDocument();
    expect(screen.getAllByTestId('process-tile')).toHaveLength(2);

    const nextButton = screen.getByRole('button', {name: /next page/i});
    const prevButton = screen.getByRole('button', {name: /previous page/i});

    expect(nextButton).toBeEnabled();
    expect(prevButton).toBeDisabled();

    await user.click(nextButton);

    expect(await screen.findByText('Process C')).toBeVisible();
    expect(screen.queryByText('Process A')).not.toBeInTheDocument();
    expect(screen.queryByText('Process B')).not.toBeInTheDocument();
    expect(screen.getAllByTestId('process-tile')).toHaveLength(1);

    expect(nextButton).toBeDisabled();
    expect(prevButton).toBeEnabled();

    await user.click(prevButton);

    expect(await screen.findByText('Process A')).toBeVisible();
    expect(screen.getByText('Process B')).toBeVisible();
    expect(screen.queryByText('Process C')).not.toBeInTheDocument();
    expect(screen.getAllByTestId('process-tile')).toHaveLength(2);

    expect(nextButton).toBeEnabled();
    expect(prevButton).toBeDisabled();
  });
});

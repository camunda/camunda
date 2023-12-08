/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
import {rest} from 'msw';
import {MemoryRouter} from 'react-router-dom';
import {Processes} from './index';
import {notificationsStore} from 'modules/stores/notifications';
import * as formMocks from 'modules/mock-schema/mocks/form';
import * as userMocks from 'modules/mock-schema/mocks/current-user';
import {DEFAULT_MOCK_CLIENT_CONFIG} from 'modules/mocks/window';
import {LocationLog} from 'modules/utils/LocationLog';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/getMockQueryClient';

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

const mockedNotificationsStore = notificationsStore as Mocked<
  typeof notificationsStore
>;

const getWrapper = () => {
  const mockClient = getMockQueryClient();

  type Props = {
    children?: React.ReactNode;
  };

  const Wrapper: React.FC<Props> = ({children}) => {
    return (
      <QueryClientProvider client={mockClient}>
        <MemoryRouter initialEntries={['/']}>
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
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res.once(ctx.json(userMocks.currentUser));
      }),
    );
  });

  it('should render an empty state message', async () => {
    window.localStorage.setItem('hasConsentedToStartProcess', 'true');
    nodeMockServer.use(
      rest.get('/v1/internal/processes', (_, res, ctx) => {
        return res(ctx.json([]));
      }),
    );

    render(<Processes />, {
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
      rest.get('/v1/internal/processes', (_, res, ctx) => {
        return res(
          ctx.json([
            createMockProcess('process-0'),
            createMockProcess('process-1'),
          ]),
        );
      }),
    );

    render(<Processes />, {
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
      rest.get('/v1/internal/processes', (_, res, ctx) => {
        return res(ctx.json([mockProcess]));
      }),
      rest.get('/v1/forms/:formId', (_, res, ctx) => {
        return res(ctx.json(formMocks.form));
      }),
    );

    render(<Processes />, {
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
  });

  it('should show an error toast when the query fails', async () => {
    window.localStorage.setItem('hasConsentedToStartProcess', 'true');
    nodeMockServer.use(
      rest.get('/v1/internal/processes', (_, res) => {
        return res.networkError('Error');
      }),
    );

    render(<Processes />, {
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
      rest.get('/v1/internal/processes', (_, res, ctx) => {
        return res(ctx.json([createMockProcess('process-0')]));
      }),
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res.once(ctx.json(userMocks.currentRestrictedUser));
      }),
    );

    render(<Processes />, {
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

  it('should render a tenant dropdown', async () => {
    window.localStorage.setItem('hasConsentedToStartProcess', 'true');
    window.clientConfig = {
      ...DEFAULT_MOCK_CLIENT_CONFIG,
      isMultiTenancyEnabled: true,
    };
    nodeMockServer.use(
      rest.get('/v1/internal/processes', (_, res, ctx) => {
        return res(ctx.json([]));
      }),
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res.once(ctx.json(userMocks.currentUserWithTenants));
      }),
    );

    render(<Processes />, {
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
      rest.get('/v1/internal/processes', (_, res, ctx) => {
        return res(ctx.json([]));
      }),
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res.once(ctx.json(userMocks.currentUserWithTenants));
      }),
    );

    render(<Processes />, {
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
      rest.get('/v1/internal/processes', (_, res, ctx) => {
        return res(ctx.json([]));
      }),
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res.once(ctx.json(userMocks.currentUser));
      }),
    );

    render(<Processes />, {
      wrapper: getWrapper(),
    });

    await waitForElementToBeRemoved(() =>
      screen.queryAllByTestId('process-skeleton'),
    );

    expect(
      screen.queryByRole('combobox', {name: 'Tenant'}),
    ).not.toBeInTheDocument();
  });
});

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {AppHeader} from '../index';
import {fireEvent, render, screen, within} from 'modules/testing-library';
import {Wrapper} from './mocks';
import {createUser} from 'modules/testUtils';
import {mockMe} from 'modules/mocks/api/v2/me';
import {MemoryRouter, useLocation} from 'react-router-dom';
import * as clientConfig from 'modules/utils/getClientConfig';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {LocationLog} from 'modules/utils/LocationLog';
import {Paths} from 'modules/Routes';
import * as compositeComponents from '@camunda/camunda-composite-components';
import {tracking} from 'modules/tracking';

vi.mock('modules/feature-flags', async (importActual) => ({
  ...(await importActual()),
  IS_NAV_V2_ENABLED: true,
}));

const LocationState: React.FC = () => {
  const location = useLocation();

  return (
    <output data-testid="location-state">
      {JSON.stringify(location.state)}
    </output>
  );
};

const OperationsWrapper: React.FC<{children?: React.ReactNode}> = ({
  children,
}) => (
  <QueryClientProvider client={getMockQueryClient()}>
    <MemoryRouter initialEntries={[Paths.batchOperations()]}>
      {children}
      <LocationLog />
    </MemoryRouter>
  </QueryClientProvider>
);

const ProcessesWrapper: React.FC<{children?: React.ReactNode}> = ({
  children,
}) => (
  <QueryClientProvider client={getMockQueryClient()}>
    <MemoryRouter initialEntries={[Paths.processes()]}>
      {children}
      <LocationLog />
    </MemoryRouter>
  </QueryClientProvider>
);

function mockC3Profile(
  canOpenModeler: boolean,
  {
    canReadTasklist = true,
    canReadOptimize = true,
  }: {
    canReadTasklist?: boolean;
    canReadOptimize?: boolean;
  } = {},
) {
  const noAccess = {
    read: false,
    create: false,
    update: false,
    delete: false,
  };
  const activeOrg = {
    uuid: 'organization-1',
    name: 'Organization One',
    permissions: {
      cluster: {
        console: {read: true},
        modeler: {read: canOpenModeler},
        tasklist: {read: canReadTasklist},
        operate: {read: true},
        optimize: {read: canReadOptimize},
        identity: {read: true},
        admin: {read: true},
      },
      org: {
        billing: noAccess,
        clusters: noAccess,
        webide: {...noAccess, read: canOpenModeler},
        users: {owner: noAccess},
        settings: noAccess,
      },
    },
  };

  vi.spyOn(compositeComponents, 'useC3Profile').mockReturnValue({
    isEnabled: true,
    theme: 'light',
    orgs: [activeOrg],
    activeOrg,
    reloadOrgs: vi.fn(),
    removeOrg: vi.fn(),
    clusters: [],
    setClusters: vi.fn(),
    reloadClusters: vi.fn(),
    resolvedTheme: 'light',
    onThemeChange: vi.fn(),
    loadClustersById: vi.fn(),
  });
}

describe('AppHeader (nav V2)', () => {
  beforeEach(() => {
    mockMe().withSuccess(createUser({authorizedComponents: ['operate']}));
  });

  it('should render the V2 sidebar navigation items', async () => {
    render(<AppHeader />, {wrapper: Wrapper});

    expect(
      await screen.findByRole('link', {name: /dashboard/i}),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', {name: /processes/i})).toBeInTheDocument();
    expect(screen.getByRole('link', {name: /decisions/i})).toBeInTheDocument();
  });

  it('should render the Operations group parent (V2-only)', async () => {
    render(<AppHeader />, {wrapper: Wrapper});

    expect(await screen.findByText('Operations')).toBeInTheDocument();
  });

  it('should preserve filter reset state when navigating to list pages', async () => {
    const {user} = render(
      <>
        <AppHeader />
        <LocationState />
      </>,
      {wrapper: Wrapper},
    );
    const navigation = await screen.findByRole('navigation', {
      name: 'Camunda Operate',
    });

    await user.click(
      within(navigation).getByRole('link', {name: /processes/i}),
    );

    expect(screen.getByTestId('pathname')).toHaveTextContent('/processes');
    expect(screen.getByTestId('search')).toHaveTextContent(
      '?active=true&suspended=true&incidents=true',
    );
    expect(screen.getByTestId('location-state')).toHaveTextContent(
      '{"refreshContent":true,"hideOptionalFilters":true}',
    );

    await user.click(
      within(navigation).getByRole('link', {name: /decisions/i}),
    );

    expect(screen.getByTestId('pathname')).toHaveTextContent('/decisions');
    expect(screen.getByTestId('search')).toHaveTextContent(
      '?evaluated=true&failed=true',
    );
    expect(screen.getByTestId('location-state')).toHaveTextContent(
      '{"refreshContent":true,"hideOptionalFilters":true}',
    );
  });

  it('should render the cluster webapp breadcrumb', async () => {
    render(<AppHeader />, {wrapper: Wrapper});

    const breadcrumb = await screen.findByRole('navigation', {
      name: 'Camunda context',
    });

    expect(within(breadcrumb).getByText('Operate')).toBeInTheDocument();
  });

  it('should navigate to the dashboard from the Operate breadcrumb', async () => {
    const {user} = render(<AppHeader />, {wrapper: ProcessesWrapper});
    const breadcrumb = await screen.findByRole('navigation', {
      name: 'Camunda context',
    });

    await user.click(within(breadcrumb).getByRole('link', {name: 'Operate'}));

    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/$/);
  });

  it('should track app switches from the breadcrumb', async () => {
    const onClick = vi.fn();
    vi.spyOn(
      compositeComponents,
      'preview_useClusterWebappBreadcrumbs',
    ).mockReturnValue([
      {
        key: 'app',
        label: 'Operate',
        dropdownItems: [
          {
            key: 'tasklist',
            label: 'Tasklist',
            onClick,
          },
        ],
      },
    ]);
    const track = vi.spyOn(tracking, 'track');
    const {user} = render(<AppHeader />, {wrapper: Wrapper});

    await screen.findByRole('link', {name: 'Operate'});
    await user.click(screen.getByRole('button', {name: 'Switch context'}));
    await user.click(screen.getByRole('menuitemradio', {name: 'Tasklist'}));

    expect(track).toHaveBeenCalledWith({
      eventName: 'app-switcher-item-clicked',
      app: 'tasklist',
    });
    expect(onClick).toHaveBeenCalled();
  });

  it('should include SaaS Console and Modeler links in the app switcher', async () => {
    mockC3Profile(true);
    vi.spyOn(clientConfig, 'getClientConfig').mockReturnValue({
      ...clientConfig.getClientConfig(),
      organizationId: 'organization-1',
      clusterId: 'cluster-1',
    });
    mockMe().withSuccess(
      createUser({
        authorizedComponents: ['operate'],
        c8Links: {
          console: 'https://console.example.com/org/organization-1',
          modeler: 'https://modeler.example.com/org/organization-1',
          tasklist: 'https://tasklist.example.com',
          optimize: 'https://optimize.example.com',
        },
      }),
    );
    vi.spyOn(
      compositeComponents,
      'preview_useClusterWebappBreadcrumbs',
    ).mockReturnValue([{key: 'app', label: 'Operate'}]);
    const {user} = render(<AppHeader />, {wrapper: Wrapper});

    await screen.findByRole('link', {name: 'Operate'});
    await user.click(screen.getByRole('button', {name: 'Switch application'}));

    expect(
      screen.getByRole('menuitemradio', {name: 'Console'}),
    ).toHaveAttribute('href', 'https://console.example.com/org/organization-1');
    expect(
      screen.getByRole('menuitemradio', {name: 'Modeler'}),
    ).toHaveAttribute('href', 'https://modeler.example.com/org/organization-1');
    expect(
      screen.getByRole('menuitemradio', {name: 'Tasklist'}),
    ).toHaveAttribute('href', 'https://tasklist.example.com');
    expect(
      screen.getByRole('menuitemradio', {name: 'Optimize'}),
    ).toHaveAttribute('href', 'https://optimize.example.com');
  });

  it('should hide the SaaS Modeler link without permission', async () => {
    mockC3Profile(false);
    vi.spyOn(clientConfig, 'getClientConfig').mockReturnValue({
      ...clientConfig.getClientConfig(),
      organizationId: 'organization-1',
      clusterId: 'cluster-1',
    });
    mockMe().withSuccess(
      createUser({
        authorizedComponents: ['operate'],
        c8Links: {
          console: 'https://console.example.com/org/organization-1',
          modeler: 'https://modeler.example.com/org/organization-1',
        },
      }),
    );
    vi.spyOn(
      compositeComponents,
      'preview_useClusterWebappBreadcrumbs',
    ).mockReturnValue([{key: 'app', label: 'Operate'}]);
    const {user} = render(<AppHeader />, {wrapper: Wrapper});

    await screen.findByRole('link', {name: 'Operate'});
    await user.click(screen.getByRole('button', {name: 'Switch application'}));

    expect(
      screen.getByRole('menuitemradio', {name: 'Console'}),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('menuitemradio', {name: 'Modeler'}),
    ).not.toBeInTheDocument();
  });

  it('should not use the organization URL as a Modeler fallback', async () => {
    mockC3Profile(true);
    vi.spyOn(clientConfig, 'getClientConfig').mockReturnValue({
      ...clientConfig.getClientConfig(),
      organizationId: 'organization-1',
      clusterId: 'cluster-1',
    });
    vi.spyOn(
      compositeComponents,
      'preview_useClusterWebappBreadcrumbs',
    ).mockReturnValue([
      {
        key: 'org',
        label: 'Organization One',
        linkProps: {
          href: 'https://console.example.com/org/organization-1',
        },
      },
      {key: 'app', label: 'Operate'},
    ]);
    const {user} = render(<AppHeader />, {wrapper: Wrapper});

    await screen.findByRole('link', {name: 'Operate'});
    await user.click(screen.getByRole('button', {name: 'Switch application'}));

    expect(
      screen.getByRole('menuitemradio', {name: 'Console'}),
    ).toHaveAttribute('href', 'https://console.example.com/org/organization-1');
    expect(
      screen.queryByRole('menuitemradio', {name: 'Modeler'}),
    ).not.toBeInTheDocument();
  });

  it('should not add direct app links without permission', async () => {
    mockC3Profile(false, {
      canReadTasklist: false,
      canReadOptimize: false,
    });
    vi.spyOn(clientConfig, 'getClientConfig').mockReturnValue({
      ...clientConfig.getClientConfig(),
      organizationId: 'organization-1',
      clusterId: 'cluster-1',
    });
    mockMe().withSuccess(
      createUser({
        authorizedComponents: ['operate'],
        c8Links: {
          tasklist: 'https://tasklist.example.com',
          optimize: 'https://optimize.example.com',
        },
      }),
    );
    vi.spyOn(
      compositeComponents,
      'preview_useClusterWebappBreadcrumbs',
    ).mockReturnValue([{key: 'app', label: 'Operate'}]);
    const {user} = render(<AppHeader />, {wrapper: Wrapper});

    await screen.findByRole('link', {name: 'Operate'});
    await user.click(screen.getByRole('button', {name: 'Switch application'}));

    expect(
      screen.queryByRole('menuitemradio', {name: 'Tasklist'}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('menuitemradio', {name: 'Optimize'}),
    ).not.toBeInTheDocument();
  });

  it('should open info links', async () => {
    const open = vi.fn();
    vi.stubGlobal('open', open);
    const {user} = render(<AppHeader />, {wrapper: Wrapper});

    await user.click(await screen.findByRole('button', {name: 'Info'}));
    expect(
      screen.getByRole('region', {name: 'Info'}).querySelector('.c4-ui'),
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Documentation'}));

    expect(open).toHaveBeenLastCalledWith('https://docs.camunda.io/', '_blank');
  });

  it('should only render feedback and support for paid plans', async () => {
    mockMe().withSuccess(
      createUser({
        authorizedComponents: ['operate'],
        salesPlanType: 'enterprise',
      }),
    );
    const {user} = render(<AppHeader />, {wrapper: Wrapper});

    await user.click(await screen.findByRole('button', {name: 'Info'}));

    expect(
      screen.getByRole('button', {name: 'Feedback and Support'}),
    ).toBeInTheDocument();
  });

  it('should render notifications only for SaaS', async () => {
    vi.spyOn(clientConfig, 'getClientConfig').mockReturnValue({
      ...clientConfig.getClientConfig(),
      organizationId: 'org-id',
    });
    const {user} = render(<AppHeader />, {wrapper: Wrapper});

    const notifications = await screen.findByRole('button', {
      name: 'Notifications, none unread',
    });
    await user.click(notifications);

    expect(
      screen.getByRole('heading', {name: 'Notifications'}),
    ).toBeInTheDocument();
  });

  it('should hide logout when the deployment cannot log out', async () => {
    vi.spyOn(clientConfig, 'getClientConfig').mockReturnValue({
      ...clientConfig.getClientConfig(),
      canLogout: false,
    });
    const {user} = render(<AppHeader />, {wrapper: Wrapper});

    await user.click(await screen.findByRole('button', {name: /user menu/i}));

    expect(
      screen.queryByRole('menuitem', {name: /log out/i}),
    ).not.toBeInTheDocument();
  });

  it('should render logout when the deployment can log out', async () => {
    vi.spyOn(clientConfig, 'getClientConfig').mockReturnValue({
      ...clientConfig.getClientConfig(),
      canLogout: true,
    });
    const {user} = render(<AppHeader />, {wrapper: Wrapper});

    await user.click(await screen.findByRole('button', {name: /user menu/i}));

    expect(
      screen.getByRole('menuitem', {name: /log out/i}),
    ).toBeInTheDocument();
  });

  it('should render keyboard-accessible theme options', async () => {
    const {user} = render(<AppHeader />, {wrapper: Wrapper});

    await user.click(await screen.findByRole('button', {name: /user menu/i}));

    expect(
      screen.getByRole('menuitemradio', {name: 'Light'}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('menuitemradio', {name: 'System'}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('menuitemradio', {name: 'Dark'}),
    ).toBeInTheDocument();
    expect(screen.getByRole('group', {name: 'Theme'})).toBeInTheDocument();
  });

  it('should allow collapsing Operations while on an Operations page', async () => {
    render(<AppHeader />, {wrapper: OperationsWrapper});
    fireEvent.click(
      await screen.findByRole('button', {name: 'Expand sidebar'}),
    );
    const toggle = screen.getByRole('button', {
      name: 'Toggle Operations',
    });

    expect(toggle).toHaveAttribute('aria-expanded', 'true');

    fireEvent.click(toggle);

    expect(toggle).toHaveAttribute('aria-expanded', 'false');
  });

  it('should hide navigation for unauthorized users', async () => {
    vi.spyOn(window, 'matchMedia').mockImplementation(
      (query): MediaQueryList => ({
        matches: query === '(width < 48rem)',
        media: query,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
      }),
    );
    mockMe().withSuccess(createUser({authorizedComponents: ['tasklist']}));

    render(<AppHeader />, {wrapper: Wrapper});

    expect(
      await screen.findByRole('button', {name: /user menu/i}),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('navigation', {name: 'Camunda Operate'}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Open navigation'}),
    ).not.toBeInTheDocument();
    expect(document.body.style.getPropertyValue('--app-sidebar-width')).toBe(
      '0px',
    );
  });
});

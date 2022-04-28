/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {MemoryRouter} from 'react-router-dom';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {Header} from './index';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {render, screen} from 'modules/testing-library';
import {processInstancesStore} from 'modules/stores/processInstances';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {storeStateLocally, clearStateLocally} from 'modules/utils/localStorage';
import {authenticationStore} from 'modules/stores/authentication';
import {LocationLog} from 'modules/utils/LocationLog';

function createWrapper(initialPath: string = '/') {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => (
    <ThemeProvider>
      <MemoryRouter initialEntries={[initialPath]}>
        {children}
        <LocationLog />
      </MemoryRouter>
    </ThemeProvider>
  );
  return Wrapper;
}

describe('Header', () => {
  beforeEach(() => {
    mockServer.use(
      rest.get('/api/authentications/user', (_, res, ctx) =>
        res.once(
          ctx.json({
            displayName: 'firstname lastname',
            canLogout: false,
            permissions: ['read', 'write'],
          })
        )
      ),
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(
          ctx.json({
            id: 'first_instance_id',
            state: 'ACTIVE',
          })
        )
      )
    );

    authenticationStore.authenticate();
  });

  afterEach(() => {
    processInstanceDetailsStore.reset();
    authenticationStore.reset();
    processInstancesStore.reset();
    clearStateLocally();
  });

  it('should render all header links', async () => {
    render(<Header />, {
      wrapper: createWrapper(),
    });

    expect(await screen.findByText('firstname lastname')).toBeInTheDocument();

    expect(screen.getByText('Operate')).toBeInTheDocument();
    expect(screen.getByText('Dashboard')).toBeInTheDocument();
    expect(screen.getByText('Processes')).toBeInTheDocument();
    expect(screen.getByText('Decisions')).toBeInTheDocument();
  });

  it('should go to the correct pages when clicking on header links', async () => {
    storeStateLocally({
      filters: {
        active: true,
        incidents: true,
        completed: true,
      },
    });

    const {user} = render(<Header />, {
      wrapper: createWrapper(),
    });

    expect(await screen.findByText('firstname lastname')).toBeInTheDocument();

    await user.click(await screen.findByText('Operate'));
    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/$/);
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.click(await screen.findByText('Processes'));
    expect(screen.getByTestId('search')).toHaveTextContent(
      /^\?active=true&incidents=true&completed=true$/
    );

    await user.click(await screen.findByText('Dashboard'));
    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/$/);
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.click(await screen.findByText('Decisions'));
    expect(screen.getByTestId('search')).toHaveTextContent(
      /^\?evaluated=true&failed=true$/
    );
  });

  describe('license note', () => {
    afterEach(() => {
      window.clientConfig = undefined;
    });

    it('should show license note in CCSM free/trial environment', async () => {
      window.clientConfig = {
        isEnterprise: false,
        organizationId: null,
      };

      render(<Header />, {
        wrapper: createWrapper(),
      });

      expect(await screen.findByText('firstname lastname')).toBeInTheDocument();

      expect(screen.getByText('Non-Production License')).toBeInTheDocument();
    });

    it('should not show license note in SaaS environment', async () => {
      window.clientConfig = {
        isEnterprise: false,
        organizationId: '000000000-0000-0000-0000-000000000000',
      };

      render(<Header />, {
        wrapper: createWrapper(),
      });

      expect(await screen.findByText('firstname lastname')).toBeInTheDocument();

      expect(
        screen.queryByText('Non-Production License')
      ).not.toBeInTheDocument();
    });

    it('should not show license note in CCSM enterprise environment', async () => {
      window.clientConfig = {
        isEnterprise: true,
        organizationId: null,
      };

      render(<Header />, {
        wrapper: createWrapper(),
      });

      expect(await screen.findByText('firstname lastname')).toBeInTheDocument();

      expect(
        screen.queryByText('Non-Production License')
      ).not.toBeInTheDocument();
    });
  });

  describe('active links', () => {
    it('should display processes as active when on processes page', async () => {
      render(<Header />, {
        wrapper: createWrapper('/processes'),
      });

      expect(await screen.findByText('firstname lastname')).toBeInTheDocument();

      expect(screen.getByTitle('View Processes')).toHaveAttribute(
        'aria-current',
        'page'
      );
    });

    it('should not display processes as active when on process detail page', async () => {
      render(<Header />, {
        wrapper: createWrapper('/processes/1'),
      });

      expect(await screen.findByText('firstname lastname')).toBeInTheDocument();

      expect(screen.getByTitle('View Processes')).not.toHaveAttribute(
        'aria-current',
        'page'
      );
    });

    it('should display decisions as active when on decisions page', async () => {
      render(<Header />, {
        wrapper: createWrapper('/decisions'),
      });

      expect(await screen.findByText('firstname lastname')).toBeInTheDocument();

      expect(screen.getByTitle('View Decisions')).toHaveAttribute(
        'aria-current',
        'page'
      );
    });

    it('should not display decisions as active when on decision detail page', async () => {
      render(<Header />, {
        wrapper: createWrapper('/decisions/1'),
      });

      expect(await screen.findByText('firstname lastname')).toBeInTheDocument();

      expect(screen.getByTitle('View Decisions')).not.toHaveAttribute(
        'aria-current',
        'page'
      );
    });

    it('should display dashboard as active when on dashboard page', async () => {
      render(<Header />, {
        wrapper: createWrapper('/'),
      });

      expect(await screen.findByText('firstname lastname')).toBeInTheDocument();

      const [logoNavLink, dashboardNavLink] =
        screen.getAllByTitle('View Dashboard');

      expect(logoNavLink).toHaveAttribute('aria-current', 'page');
      expect(dashboardNavLink).toHaveAttribute('aria-current', 'page');
    });
  });
});

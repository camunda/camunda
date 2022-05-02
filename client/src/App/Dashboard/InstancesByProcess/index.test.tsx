/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {render, within, screen} from 'modules/testing-library';
import {InstancesByProcess} from './index';
import {
  mockWithSingleVersion,
  mockErrorResponse,
  mockEmptyResponse,
  mockWithMultipleVersions,
} from './index.setup';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {panelStatesStore} from 'modules/stores/panelStates';
import {LocationLog} from 'modules/utils/LocationLog';

function createWrapper(initialPath: string = '/') {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    return (
      <ThemeProvider>
        <MemoryRouter initialEntries={[initialPath]}>
          <Routes>
            <Route path="/processes" element={<div>Processes</div>} />
            <Route path="/" element={children} />
          </Routes>
          <LocationLog />
        </MemoryRouter>
      </ThemeProvider>
    );
  };

  return Wrapper;
}

describe('InstancesByProcess', () => {
  beforeEach(() => {
    panelStatesStore.toggleFiltersPanel();
  });

  afterEach(() => {
    panelStatesStore.reset();
  });

  it('should display skeleton when loading', async () => {
    mockServer.use(
      rest.get('/api/incidents/byProcess', (_, res, ctx) =>
        res.once(ctx.json(mockWithSingleVersion))
      )
    );

    render(<InstancesByProcess />, {
      wrapper: createWrapper(),
    });

    expect(screen.getByTestId('skeleton')).toBeInTheDocument();

    expect(
      await screen.findByTestId('instances-by-process')
    ).toBeInTheDocument();

    expect(screen.queryByTestId('skeleton')).not.toBeInTheDocument();
  });

  it('should handle server errors', async () => {
    mockServer.use(
      rest.get('/api/incidents/byProcess', (_, res, ctx) =>
        res.once(ctx.status(500), ctx.json(mockErrorResponse))
      )
    );

    render(<InstancesByProcess />, {
      wrapper: createWrapper(),
    });

    expect(
      await screen.findByText('Data could not be fetched')
    ).toBeInTheDocument();
  });

  it('should handle network errors', async () => {
    mockServer.use(
      rest.get('/api/incidents/byProcess', (_, res, ctx) =>
        res.networkError('A network error')
      )
    );

    render(<InstancesByProcess />, {
      wrapper: createWrapper(),
    });

    expect(
      await screen.findByText('Data could not be fetched')
    ).toBeInTheDocument();
  });

  it('should display information message when there are no processes', async () => {
    mockServer.use(
      rest.get('/api/incidents/byProcess', (_, res, ctx) =>
        res.once(ctx.json(mockEmptyResponse))
      )
    );

    render(<InstancesByProcess />, {
      wrapper: createWrapper(),
    });

    expect(
      await screen.findByText('There are no Processes deployed')
    ).toBeInTheDocument();
  });

  it('should render items with more than one processes versions', async () => {
    mockServer.use(
      rest.get('/api/incidents/byProcess', (_, res, ctx) =>
        res.once(ctx.json(mockWithMultipleVersions))
      ),
      rest.get('/api/incidents/byProcess', (_, res, ctx) =>
        res.once(ctx.json(mockWithMultipleVersions))
      ),
      rest.get('/api/incidents/byProcess', (_, res, ctx) =>
        res.once(ctx.json(mockWithMultipleVersions))
      )
    );

    const {user} = render(<InstancesByProcess />, {
      wrapper: createWrapper(),
    });

    const withinIncident = within(
      await screen.findByTestId('incident-byProcess-0')
    );

    const processLink = withinIncident.getByRole('link', {
      name: /View 201 Instances in 2 Versions of Process Order process/,
    });

    expect(processLink).toHaveAttribute(
      'href',
      '/processes?process=orderProcess&version=all&active=true&incidents=true'
    );

    expect(screen.getByTestId('incident-instances-badge')).toHaveTextContent(
      '65'
    );
    expect(screen.getByTestId('active-instances-badge')).toHaveTextContent(
      '136'
    );

    const expandButton = withinIncident.getByTitle(
      'Expand 201 Instances of Process Order process'
    );

    await user.click(expandButton);

    const firstVersion = screen.getByRole('link', {
      name: /View 42 Instances in Version 1 of Process First Version/,
    });

    expect(
      within(firstVersion).getByTestId('incident-instances-badge')
    ).toHaveTextContent('37');
    expect(
      within(firstVersion).getByTestId('active-instances-badge')
    ).toHaveTextContent('5');
    expect(
      within(firstVersion).getByText(
        'First Version – 42 Instances in Version 1'
      )
    ).toBeInTheDocument();
    expect(firstVersion).toHaveAttribute(
      'href',
      '/processes?process=mockProcess&version=1&active=true&incidents=true'
    );

    const secondVersion = screen.getByRole('link', {
      name: 'View 42 Instances in Version 2 of Process Second Version',
    });

    expect(
      within(secondVersion).getByTestId('incident-instances-badge')
    ).toHaveTextContent('37');
    expect(
      within(secondVersion).getByTestId('active-instances-badge')
    ).toHaveTextContent('5');
    expect(
      within(secondVersion).getByText(
        'Second Version – 42 Instances in Version 2'
      )
    ).toBeInTheDocument();
    expect(secondVersion).toHaveAttribute(
      'href',
      '/processes?process=mockProcess&version=2&active=true&incidents=true'
    );
  });

  it('should render items with one process version', async () => {
    mockServer.use(
      rest.get('/api/incidents/byProcess', (_, res, ctx) =>
        res.once(ctx.json(mockWithSingleVersion))
      )
    );

    render(<InstancesByProcess />, {
      wrapper: createWrapper(),
    });

    const withinIncident = within(
      await screen.findByTestId('incident-byProcess-0')
    );

    expect(
      withinIncident.queryByTestId('expand-button')
    ).not.toBeInTheDocument();

    expect(
      withinIncident.getByText('loanProcess – 138 Instances in 1 Version')
    ).toBeInTheDocument();

    const processLink = withinIncident.getByRole('link', {
      name: 'View 138 Instances in 1 Version of Process loanProcess',
    });

    expect(processLink).toHaveAttribute(
      'href',
      '/processes?process=loanProcess&version=1&active=true&incidents=true'
    );

    expect(screen.getByTestId('incident-instances-badge')).toHaveTextContent(
      '16'
    );
    expect(screen.getByTestId('active-instances-badge')).toHaveTextContent(
      '122'
    );
  });

  it('should expand filters panel on click', async () => {
    mockServer.use(
      rest.get('/api/incidents/byProcess', (_, res, ctx) =>
        res.once(ctx.json(mockWithSingleVersion))
      )
    );

    const {user} = render(<InstancesByProcess />, {
      wrapper: createWrapper(),
    });

    expect(panelStatesStore.state.isFiltersCollapsed).toBe(true);

    const withinIncident = within(
      await screen.findByTestId('incident-byProcess-0')
    );

    const processLink = withinIncident.getByRole('link', {
      name: 'View 138 Instances in 1 Version of Process loanProcess',
    });

    await user.click(processLink);

    expect(screen.getByTestId('search')).toHaveTextContent(
      /^\?process=loanProcess&version=1&active=true&incidents=true$/
    );
    expect(panelStatesStore.state.isFiltersCollapsed).toBe(false);
  });

  it('should update after next poll', async () => {
    jest.useFakeTimers();
    mockServer.use(
      rest.get('/api/incidents/byProcess', (_, res, ctx) =>
        res.once(ctx.json(mockWithSingleVersion))
      )
    );

    render(<InstancesByProcess />, {
      wrapper: createWrapper(),
    });

    const withinIncident = within(
      await screen.findByTestId('incident-byProcess-0')
    );

    expect(
      withinIncident.getByText('loanProcess – 138 Instances in 1 Version')
    ).toBeInTheDocument();

    mockServer.use(
      rest.get('/api/incidents/byProcess', (_, res, ctx) =>
        res.once(
          ctx.json([{...mockWithSingleVersion[0], activeInstancesCount: 142}])
        )
      )
    );

    jest.runOnlyPendingTimers();

    expect(
      await withinIncident.findByText(
        'loanProcess – 158 Instances in 1 Version'
      )
    ).toBeInTheDocument();

    jest.clearAllTimers();
    jest.useRealTimers();
  });
});

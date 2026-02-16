/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {render, within, screen, waitFor} from 'modules/testing-library';
import {InstancesByProcessDefinition} from './index';
import {
  mockWithSingleVersion,
  mockWithMultipleVersions,
  mockOrderProcessVersions,
} from './index.setup';
import {panelStatesStore} from 'modules/stores/panelStates';
import {LocationLog} from 'modules/utils/LocationLog';
import {mockFetchProcessDefinitionVersionStatistics} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionVersionStatistics';
import {useEffect} from 'react';
import {Paths} from 'modules/Routes';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockMe} from 'modules/mocks/api/v2/me';
import {createUser} from 'modules/testUtils';

function createWrapper(initialPath: string = Paths.dashboard()) {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        panelStatesStore.reset();
      };
    }, []);

    return (
      <QueryClientProvider client={getMockQueryClient()}>
        <MemoryRouter initialEntries={[initialPath]}>
          <Routes>
            <Route path={Paths.processes()} element={<div>Processes</div>} />
            <Route path={Paths.dashboard()} element={children} />
          </Routes>
          <LocationLog />
        </MemoryRouter>
      </QueryClientProvider>
    );
  };

  return Wrapper;
}

describe('InstancesByProcessDefinition', () => {
  beforeEach(() => {
    panelStatesStore.toggleFiltersPanel();
    mockMe().withSuccess(createUser());
  });

  it('should display skeleton when loading', () => {
    render(<InstancesByProcessDefinition status="pending" items={[]} />, {
      wrapper: createWrapper(),
    });

    expect(screen.getByTestId('data-table-skeleton')).toBeInTheDocument();
  });

  it('should handle errors', () => {
    render(<InstancesByProcessDefinition status="error" items={[]} />, {
      wrapper: createWrapper(),
    });

    expect(screen.getByText('Data could not be fetched')).toBeInTheDocument();
  });

  it('should render items with more than one processes versions', async () => {
    mockFetchProcessDefinitionVersionStatistics().withSuccess(
      mockOrderProcessVersions,
    );

    const {user} = render(
      <InstancesByProcessDefinition
        status="success"
        items={mockWithMultipleVersions.items}
      />,
      {
        wrapper: createWrapper(),
      },
    );

    const withinIncident = within(
      await screen.findByTestId('instances-by-process-definition-0'),
    );

    const processLink = withinIncident.getByRole('link', {
      description:
        /View 201 Instances in 2\+ Versions of Process Order process/,
    });

    expect(processLink).toHaveAttribute(
      'href',
      `${Paths.processes()}?process=orderProcess&version=all&active=true&incidents=true`,
    );

    expect(
      within(processLink).getByTestId('incident-instances-badge'),
    ).toHaveTextContent('65');
    expect(
      within(processLink).getByTestId('active-instances-badge'),
    ).toHaveTextContent('136');

    const expandButton = withinIncident.getByRole('button', {
      name: 'Expand current row',
    });

    // this button click has no effect (check useEffect in Collapse component)
    await user.click(expandButton);

    const firstVersion = await screen.findByRole('link', {
      description: /View 42 Instances in Version 1 of Process First Version/,
    });

    expect(
      within(firstVersion).getByTestId('incident-instances-badge'),
    ).toHaveTextContent('37');
    expect(
      within(firstVersion).getByTestId('active-instances-badge'),
    ).toHaveTextContent('5');
    expect(
      within(firstVersion).getByText(
        'First Version – 42 Instances in Version 1',
      ),
    ).toBeInTheDocument();
    expect(firstVersion).toHaveAttribute(
      'href',
      `${Paths.processes()}?process=mockProcess&version=1&active=true&incidents=true`,
    );

    const secondVersion = screen.getByRole('link', {
      description: 'View 42 Instances in Version 2 of Process Second Version',
    });

    expect(
      within(secondVersion).getByTestId('incident-instances-badge'),
    ).toHaveTextContent('37');
    expect(
      within(secondVersion).getByTestId('active-instances-badge'),
    ).toHaveTextContent('5');
    expect(
      within(secondVersion).getByText(
        'Second Version – 42 Instances in Version 2',
      ),
    ).toBeInTheDocument();
    expect(secondVersion).toHaveAttribute(
      'href',
      `${Paths.processes()}?process=mockProcess&version=2&active=true&incidents=true`,
    );
  });

  it('should render items with one process version', () => {
    render(
      <InstancesByProcessDefinition
        status="success"
        items={mockWithSingleVersion.items}
      />,
      {
        wrapper: createWrapper(),
      },
    );

    const withinIncident = within(
      screen.getByTestId('instances-by-process-definition-0'),
    );

    expect(
      withinIncident.queryByTestId('expand-button'),
    ).not.toBeInTheDocument();

    expect(
      withinIncident.getByText('loanProcess – 138 Instances in 1 Version'),
    ).toBeInTheDocument();

    const processLink = screen.getByRole('link', {
      description: 'View 138 Instances in 1 Version of Process loanProcess',
    });

    expect(processLink).toHaveAttribute(
      'href',
      `${Paths.processes()}?process=loanProcess&version=1&active=true&incidents=true`,
    );

    expect(screen.getByTestId('incident-instances-badge')).toHaveTextContent(
      '16',
    );
    expect(screen.getByTestId('active-instances-badge')).toHaveTextContent(
      '122',
    );
  });

  it('should expand filters panel on click', async () => {
    const {user} = render(
      <InstancesByProcessDefinition
        status="success"
        items={mockWithSingleVersion.items}
      />,
      {
        wrapper: createWrapper(),
      },
    );

    expect(panelStatesStore.state.isFiltersCollapsed).toBe(true);

    const processLink = screen.getByRole('link', {
      description: 'View 138 Instances in 1 Version of Process loanProcess',
    });

    await user.click(processLink);

    await waitFor(() =>
      expect(screen.getByTestId('search')).toHaveTextContent(
        /^\?process=loanProcess&version=1&active=true&incidents=true$/,
      ),
    );
    expect(panelStatesStore.state.isFiltersCollapsed).toBe(false);
  });
});

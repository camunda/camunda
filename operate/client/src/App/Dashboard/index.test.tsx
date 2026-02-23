/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {render, screen} from 'modules/testing-library';
import {MemoryRouter} from 'react-router-dom';
import {PAGE_TITLE} from 'modules/constants';
import {Dashboard} from './index';
import {mockIncidentsByError} from './IncidentsByError/index.setup';
import {mockWithSingleVersion} from './InstancesByProcessDefinition/index.setup';
import {mockFetchIncidentProcessInstanceStatisticsByError} from 'modules/mocks/api/v2/incidents/fetchIncidentProcessInstanceStatisticsByError';
import {mockFetchProcessDefinitionStatistics} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionStatistics';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockMe} from 'modules/mocks/api/v2/me';
import {createUser, searchResult} from 'modules/testUtils';
import {createProcessDefinitionInstancesStatistics} from 'modules/mocks/mockProcessDefinitionStatistics';

type Props = {
  children?: React.ReactNode;
};

const Wrapper = ({children}: Props) => {
  return (
    <QueryClientProvider client={getMockQueryClient()}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
};

describe('Dashboard', () => {
  beforeEach(() => {
    mockMe().withSuccess(createUser());
  });

  it('should render', async () => {
    mockFetchIncidentProcessInstanceStatisticsByError().withSuccess(
      mockIncidentsByError,
    );
    mockFetchProcessDefinitionStatistics().withSuccess(mockWithSingleVersion);
    mockFetchProcessDefinitionStatistics().withSuccess(
      searchResult([
        createProcessDefinitionInstancesStatistics({
          activeInstancesWithIncidentCount: 877,
          activeInstancesWithoutIncidentCount: 210,
        }),
      ]),
    );

    render(<Dashboard />, {wrapper: Wrapper});

    expect(
      await screen.findByText('1087 Running Process Instances in total'),
    ).toBeInTheDocument();

    expect(document.title).toBe(PAGE_TITLE.DASHBOARD);
    expect(screen.getByText('Operate Dashboard')).toBeInTheDocument();
    expect(screen.getByText('Process Instances by Name')).toBeInTheDocument();
    expect(
      screen.getByText('Process Incidents by Error Message'),
    ).toBeInTheDocument();
  });

  it('should render empty state (no instances)', async () => {
    mockFetchIncidentProcessInstanceStatisticsByError().withSuccess(
      mockIncidentsByError,
    );
    mockFetchProcessDefinitionStatistics().withSuccess(searchResult([]));
    mockFetchProcessDefinitionStatistics().withSuccess(searchResult([]));

    render(<Dashboard />, {wrapper: Wrapper});

    expect(
      await screen.findByText('Start by deploying a process'),
    ).toBeInTheDocument();
    expect(
      screen.queryByText('Process Incidents by Error Message'),
    ).not.toBeInTheDocument();
  });

  it('should render empty state (no incidents)', async () => {
    mockFetchIncidentProcessInstanceStatisticsByError().withSuccess(
      searchResult([]),
    );
    mockFetchProcessDefinitionStatistics().withSuccess(mockWithSingleVersion);
    mockFetchProcessDefinitionStatistics().withSuccess(mockWithSingleVersion);

    render(<Dashboard />, {wrapper: Wrapper});

    expect(
      await screen.findByText('Your processes are healthy'),
    ).toBeInTheDocument();
    expect(screen.getByText('Process Instances by Name')).toBeInTheDocument();
    expect(
      screen.getByText('Process Incidents by Error Message'),
    ).toBeInTheDocument();
  });
});

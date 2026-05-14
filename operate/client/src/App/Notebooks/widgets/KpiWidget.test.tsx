/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {render, screen} from 'modules/testing-library';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {http, HttpResponse} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {KpiWidget} from './KpiWidget';
import type {WidgetConfig} from '../types';

type Props = {children?: React.ReactNode};

const Wrapper = ({children}: Props) => (
  <QueryClientProvider client={getMockQueryClient()}>
    {children}
  </QueryClientProvider>
);

const kpiConfig: WidgetConfig = {
  id: 'w-kpi-test',
  type: 'kpi',
  title: 'Process health',
  query: {endpoint: '/__notebook_kpi__', method: 'GET'},
  kpis: [
    {
      label: 'Active instances',
      query: {
        endpoint: '/v2/process-instances/search',
        method: 'POST',
        body: {filter: {state: 'ACTIVE'}, page: {limit: 1}},
      },
      field: 'page.totalItems',
      accent: 'info',
    },
    {
      label: 'With incidents',
      query: {
        endpoint: '/v2/incidents/search',
        method: 'POST',
        body: {filter: {state: 'ACTIVE'}, page: {limit: 1}},
      },
      field: 'page.totalItems',
      accent: 'error',
    },
  ],
};

describe('<KpiWidget />', () => {
  it('should render the title and a grid with one cell per KPI', async () => {
    // given
    mockServer.use(
      http.post(
        '/v2/process-instances/search',
        () =>
          HttpResponse.json({
            page: {totalItems: 42, firstSortValues: [], lastSortValues: []},
            items: [],
          }),
        {once: true},
      ),
      http.post(
        '/v2/incidents/search',
        () =>
          HttpResponse.json({
            page: {totalItems: 3, firstSortValues: [], lastSortValues: []},
            items: [],
          }),
        {once: true},
      ),
    );

    // when
    render(<KpiWidget config={kpiConfig} />, {wrapper: Wrapper});

    // then — title renders
    expect(screen.getByText('Process health')).toBeInTheDocument();

    // both cell labels render
    expect(await screen.findByText('Active instances')).toBeInTheDocument();
    expect(await screen.findByText('With incidents')).toBeInTheDocument();

    // values from API render
    expect(await screen.findByText('42')).toBeInTheDocument();
    expect(await screen.findByText('3')).toBeInTheDocument();
  });

  it('should render loading skeletons while cells are fetching', async () => {
    // given — servers never resolve
    mockServer.use(
      http.post('/v2/process-instances/search', async () => {
        await new Promise(() => {});
        return HttpResponse.json({});
      }),
      http.post('/v2/incidents/search', async () => {
        await new Promise(() => {});
        return HttpResponse.json({});
      }),
    );

    // when
    render(<KpiWidget config={kpiConfig} />, {wrapper: Wrapper});

    // then — at least one loading cell is present
    const loadingCells = screen.getAllByTestId('kpi-cell-loading');
    expect(loadingCells.length).toBeGreaterThan(0);
  });

  it('should render a dash for cells that fail to fetch', async () => {
    // given
    mockServer.use(
      http.post('/v2/process-instances/search', () => HttpResponse.error(), {
        once: true,
      }),
      http.post('/v2/incidents/search', () => HttpResponse.error(), {
        once: true,
      }),
    );

    // when
    render(<KpiWidget config={kpiConfig} />, {wrapper: Wrapper});

    // then — error cells render an em-dash placeholder
    const dashes = await screen.findAllByText('—');
    expect(dashes.length).toBeGreaterThan(0);
  });

  it('should show a fallback message when kpis array is empty', () => {
    // given
    const emptyConfig: WidgetConfig = {
      ...kpiConfig,
      id: 'w-kpi-empty',
      kpis: [],
    };

    // when
    render(<KpiWidget config={emptyConfig} />, {wrapper: Wrapper});

    // then
    expect(screen.getByText(/no kpi items configured/i)).toBeInTheDocument();
  });
});

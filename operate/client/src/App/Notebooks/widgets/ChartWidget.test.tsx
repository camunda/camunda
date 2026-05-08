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
import {ChartWidget} from './ChartWidget';
import {buildChartData} from './chartUtils';
import type {WidgetConfig} from '../types';

// ---------------------------------------------------------------------------
// Stub Carbon Charts so D3 is not invoked inside jsdom
// ---------------------------------------------------------------------------

vi.mock('@carbon/charts-react', () => ({
  SimpleBarChart: ({data}: {data: unknown[]}) => (
    <div data-testid="bar-chart-stub" data-count={data.length} />
  ),
  LineChart: ({data}: {data: unknown[]}) => (
    <div data-testid="line-chart-stub" data-count={data.length} />
  ),
  DonutChart: ({data}: {data: unknown[]}) => (
    <div data-testid="donut-chart-stub" data-count={data.length} />
  ),
  PieChart: ({data}: {data: unknown[]}) => (
    <div data-testid="pie-chart-stub" data-count={data.length} />
  ),
  StackedBarChart: ({data}: {data: unknown[]}) => (
    <div data-testid="stacked-bar-chart-stub" data-count={data.length} />
  ),
  StackedAreaChart: ({data}: {data: unknown[]}) => (
    <div data-testid="stacked-area-chart-stub" data-count={data.length} />
  ),
  MeterChart: ({data}: {data: unknown[]}) => (
    <div data-testid="meter-chart-stub" data-count={data.length} />
  ),
  TreemapChart: ({data}: {data: unknown[]}) => (
    <div data-testid="treemap-chart-stub" data-count={data.length} />
  ),
  RadarChart: ({data}: {data: unknown[]}) => (
    <div data-testid="radar-chart-stub" data-count={data.length} />
  ),
}));

// Also stub the CSS import so Vite doesn't choke on it in test mode
vi.mock('@carbon/charts-react/styles.css', () => ({}));

// ---------------------------------------------------------------------------
// Test helpers
// ---------------------------------------------------------------------------

type Props = {children?: React.ReactNode};

const Wrapper = ({children}: Props) => (
  <QueryClientProvider client={getMockQueryClient()}>
    {children}
  </QueryClientProvider>
);

const barConfig: WidgetConfig = {
  id: 'w-chart-bar-test',
  type: 'chart',
  title: 'Incidents by error type',
  chartType: 'bar',
  chartGroupBy: 'errorType',
  query: {
    endpoint: '/v2/incidents/search',
    method: 'POST',
    body: {page: {limit: 1000}},
  },
};

// ---------------------------------------------------------------------------
// Unit tests for buildChartData helper
// ---------------------------------------------------------------------------

describe('buildChartData', () => {
  it('should count items per group when no valueField is given', () => {
    // given
    const items = [
      {errorType: 'IO_MAPPING_ERROR'},
      {errorType: 'IO_MAPPING_ERROR'},
      {errorType: 'JOB_NO_RETRIES'},
    ];

    // when
    const result = buildChartData(items, 'errorType');

    // then — sorted descending by count
    expect(result[0]).toEqual({group: 'IO_MAPPING_ERROR', value: 2});
    expect(result[1]).toEqual({group: 'JOB_NO_RETRIES', value: 1});
  });

  it('should sum a numeric valueField per group', () => {
    // given
    const items = [
      {region: 'EU', amount: 100},
      {region: 'US', amount: 50},
      {region: 'EU', amount: 200},
    ];

    // when
    const result = buildChartData(items, 'region', 'amount');

    // then
    const eu = result.find((d) => d.group === 'EU');
    const us = result.find((d) => d.group === 'US');
    expect(eu?.value).toBe(300);
    expect(us?.value).toBe(50);
  });

  it('should label missing group values as (unknown)', () => {
    // given
    const items = [{errorType: null}, {errorType: undefined}, {}];

    // when
    const result = buildChartData(items, 'errorType');

    // then
    expect(result[0]).toEqual({group: '(unknown)', value: 3});
  });
});

// ---------------------------------------------------------------------------
// Component tests
// ---------------------------------------------------------------------------

describe('<ChartWidget />', () => {
  it('should render the bar chart when data loads successfully', async () => {
    // given
    mockServer.use(
      http.post(
        '/v2/incidents/search',
        () =>
          HttpResponse.json({
            page: {totalItems: 3},
            items: [
              {incidentKey: '1', errorType: 'IO_MAPPING_ERROR'},
              {incidentKey: '2', errorType: 'IO_MAPPING_ERROR'},
              {incidentKey: '3', errorType: 'JOB_NO_RETRIES'},
            ],
          }),
        {once: true},
      ),
    );

    // when
    render(<ChartWidget config={barConfig} />, {wrapper: Wrapper});

    // then — title is rendered
    expect(
      await screen.findByText('Incidents by error type'),
    ).toBeInTheDocument();

    // and the chart stub is mounted with 2 groups (IO_MAPPING_ERROR, JOB_NO_RETRIES)
    const stub = await screen.findByTestId('bar-chart-stub');
    expect(stub).toBeInTheDocument();
    expect(stub).toHaveAttribute('data-count', '2');
  });

  it('should render a loading state while data is loading', async () => {
    // given — server never responds
    mockServer.use(
      http.post('/v2/incidents/search', async () => {
        await new Promise(() => {});
        return HttpResponse.json({});
      }),
    );

    // when
    render(<ChartWidget config={barConfig} />, {wrapper: Wrapper});

    // then
    expect(screen.getByTestId('chart-loading')).toBeInTheDocument();
  });

  it('should render an error state on fetch failure', async () => {
    // given
    mockServer.use(
      http.post('/v2/incidents/search', () => HttpResponse.error(), {
        once: true,
      }),
    );

    // when
    render(<ChartWidget config={barConfig} />, {wrapper: Wrapper});

    // then
    expect(
      await screen.findByText(/could not load chart/i),
    ).toBeInTheDocument();
    expect(screen.getByTestId('chart-error')).toBeInTheDocument();
  });

  it('should render an empty state when items array is empty', async () => {
    // given
    mockServer.use(
      http.post(
        '/v2/incidents/search',
        () =>
          HttpResponse.json({
            page: {totalItems: 0},
            items: [],
          }),
        {once: true},
      ),
    );

    // when
    render(<ChartWidget config={barConfig} />, {wrapper: Wrapper});

    // then
    expect(await screen.findByText(/no data available/i)).toBeInTheDocument();
    expect(screen.getByTestId('chart-empty')).toBeInTheDocument();
  });

  it('should render a donut chart when chartType is donut', async () => {
    // given
    const donutConfig: WidgetConfig = {
      ...barConfig,
      id: 'w-chart-donut-test',
      title: 'Instances by state',
      chartType: 'donut',
      chartGroupBy: 'state',
      query: {
        endpoint: '/v2/process-instances/search',
        method: 'POST',
        body: {page: {limit: 1000}},
      },
    };

    mockServer.use(
      http.post(
        '/v2/process-instances/search',
        () =>
          HttpResponse.json({
            items: [
              {processInstanceKey: '1', state: 'ACTIVE'},
              {processInstanceKey: '2', state: 'ACTIVE'},
              {processInstanceKey: '3', state: 'COMPLETED'},
            ],
          }),
        {once: true},
      ),
    );

    // when
    render(<ChartWidget config={donutConfig} />, {wrapper: Wrapper});

    // then
    expect(await screen.findByTestId('donut-chart-stub')).toBeInTheDocument();
    expect(screen.getByTestId('donut-chart-stub')).toHaveAttribute(
      'data-count',
      '2',
    );
  });

  it('should render a pie chart when chartType is pie', async () => {
    // given
    const pieConfig: WidgetConfig = {
      ...barConfig,
      id: 'w-chart-pie-test',
      title: 'Instances by state (pie)',
      chartType: 'pie',
      chartGroupBy: 'state',
      query: {
        endpoint: '/v2/process-instances/search',
        method: 'POST',
        body: {page: {limit: 1000}},
      },
    };

    mockServer.use(
      http.post(
        '/v2/process-instances/search',
        () =>
          HttpResponse.json({
            items: [{state: 'ACTIVE'}, {state: 'ACTIVE'}, {state: 'COMPLETED'}],
          }),
        {once: true},
      ),
    );

    // when
    render(<ChartWidget config={pieConfig} />, {wrapper: Wrapper});

    // then
    expect(await screen.findByTestId('pie-chart-stub')).toBeInTheDocument();
    expect(screen.getByTestId('pie-chart-stub')).toHaveAttribute(
      'data-count',
      '2',
    );
  });

  it('should render a stacked-area chart when chartType is stacked-area', async () => {
    // given
    const areaConfig: WidgetConfig = {
      ...barConfig,
      id: 'w-chart-area-test',
      title: 'Instances by process (area)',
      chartType: 'stacked-area',
      chartGroupBy: 'processDefinitionId',
      chartStackBy: 'state',
      query: {
        endpoint: '/v2/process-instances/search',
        method: 'POST',
        body: {page: {limit: 1000}},
      },
    };

    mockServer.use(
      http.post(
        '/v2/process-instances/search',
        () =>
          HttpResponse.json({
            items: [
              {processDefinitionId: 'order-process', state: 'ACTIVE'},
              {processDefinitionId: 'order-process', state: 'COMPLETED'},
              {processDefinitionId: 'payment-process', state: 'ACTIVE'},
            ],
          }),
        {once: true},
      ),
    );

    // when
    render(<ChartWidget config={areaConfig} />, {wrapper: Wrapper});

    // then
    expect(
      await screen.findByTestId('stacked-area-chart-stub'),
    ).toBeInTheDocument();
  });

  it('should render a line chart when chartType is line', async () => {
    // given
    const lineConfig: WidgetConfig = {
      ...barConfig,
      id: 'w-chart-line-test',
      title: 'Incidents over time',
      chartType: 'line',
      query: {
        endpoint: '/v2/incidents/search',
        method: 'POST',
        body: {page: {limit: 1000}},
      },
    };

    mockServer.use(
      http.post(
        '/v2/incidents/search',
        () =>
          HttpResponse.json({
            items: [
              {incidentKey: '1', errorType: 'IO_MAPPING_ERROR'},
              {incidentKey: '2', errorType: 'JOB_NO_RETRIES'},
            ],
          }),
        {once: true},
      ),
    );

    // when
    render(<ChartWidget config={lineConfig} />, {wrapper: Wrapper});

    // then
    expect(await screen.findByTestId('line-chart-stub')).toBeInTheDocument();
  });
});

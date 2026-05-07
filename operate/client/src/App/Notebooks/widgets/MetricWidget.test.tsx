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
import {MetricWidget} from './MetricWidget';
import type {WidgetConfig} from '../types';

type Props = {children?: React.ReactNode};

const Wrapper = ({children}: Props) => (
  <QueryClientProvider client={getMockQueryClient()}>
    {children}
  </QueryClientProvider>
);

const metricConfig: WidgetConfig = {
  id: 'w-metric-test',
  type: 'metric',
  title: 'Running Instances',
  query: {
    endpoint: '/v2/process-instances/search',
    method: 'POST',
    body: {filter: {state: 'ACTIVE'}},
  },
  field: 'page.totalItems',
};

describe('<MetricWidget />', () => {
  it('should render a loading skeleton while data is loading', async () => {
    // given – server never resolves so we stay in loading state
    mockServer.use(
      http.post('/v2/process-instances/search', async () => {
        // delay indefinitely for this test
        await new Promise(() => {});
        return HttpResponse.json({});
      }),
    );

    // when
    render(<MetricWidget config={metricConfig} />, {wrapper: Wrapper});

    // then – a skeleton / loading indicator must be visible immediately
    expect(
      screen.getByRole('status', {hidden: true}) ??
        screen.getByTestId('metric-skeleton'),
    ).toBeInTheDocument();
  });

  it('should render the title and the value from response.page.totalItems by default', async () => {
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
    );

    // when
    render(<MetricWidget config={metricConfig} />, {wrapper: Wrapper});

    // then
    expect(await screen.findByText('Running Instances')).toBeInTheDocument();
    expect(await screen.findByText('42')).toBeInTheDocument();
  });

  it('should render an error state on fetch failure', async () => {
    // given
    mockServer.use(
      http.post('/v2/process-instances/search', () => HttpResponse.error(), {
        once: true,
      }),
    );

    // when
    render(<MetricWidget config={metricConfig} />, {wrapper: Wrapper});

    // then
    expect(
      await screen.findByText(/error|failed|could not/i),
    ).toBeInTheDocument();
  });
});

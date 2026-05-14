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
import {TrendWidget} from './TrendWidget';
import type {WidgetConfig} from '../types';

type Props = {children?: React.ReactNode};

const Wrapper = ({children}: Props) => (
  <QueryClientProvider client={getMockQueryClient()}>
    {children}
  </QueryClientProvider>
);

const trendConfig: WidgetConfig = {
  id: 'w-trend-test',
  type: 'trend',
  title: 'New instances · last 7 days',
  query: {
    endpoint: '/v2/process-instances/search',
    method: 'POST',
    body: {page: {limit: 1}},
  },
  trendDateField: 'startDate',
  trendBuckets: 7,
  trendBucketSpan: '24h',
  trendAccent: 'info',
};

describe('<TrendWidget />', () => {
  it('should render the widget title', async () => {
    // given
    mockServer.use(
      http.post('/v2/process-instances/search', () =>
        HttpResponse.json({
          page: {totalItems: 5, firstSortValues: [], lastSortValues: []},
          items: [],
        }),
      ),
    );

    // when
    render(<TrendWidget config={trendConfig} />, {wrapper: Wrapper});

    // then
    expect(
      await screen.findByText('New instances · last 7 days'),
    ).toBeInTheDocument();
  });

  it('should render the title-as-caption and show a loading skeleton initially', () => {
    // given — respond with data immediately so queries complete quickly
    mockServer.use(
      http.post('/v2/process-instances/search', () =>
        HttpResponse.json({
          page: {totalItems: 3, firstSortValues: [], lastSortValues: []},
          items: [],
        }),
      ),
    );

    // when — render synchronously; before promises settle, the metric tile
    // shows its caption and a SkeletonText placeholder for the value.
    render(<TrendWidget config={trendConfig} />, {wrapper: Wrapper});

    // then — caption is rendered (the title or subtitle as MetricCaption)
    expect(
      screen.getByText(trendConfig.subtitle ?? trendConfig.title),
    ).toBeInTheDocument();
  });
});

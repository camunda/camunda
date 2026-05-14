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
import {ActivityFeedWidget} from './ActivityFeedWidget';
import type {WidgetConfig} from '../types';

type Props = {children?: React.ReactNode};

const Wrapper = ({children}: Props) => (
  <QueryClientProvider client={getMockQueryClient()}>
    {children}
  </QueryClientProvider>
);

// ---------------------------------------------------------------------------
// Single-source smoke tests
// ---------------------------------------------------------------------------

const singleSourceConfig: WidgetConfig = {
  id: 'af-single-test',
  type: 'activity-feed',
  title: 'Recent incidents',
  query: {
    endpoint: '/v2/incidents/search',
    method: 'POST',
    body: {page: {limit: 50}},
  },
  activityTitleField: 'errorType',
  activitySubtitleField: 'errorMessage',
  activityTimeField: 'creationTime',
};

describe('<ActivityFeedWidget /> — single source', () => {
  it('should render activity items from the response', async () => {
    // given
    mockServer.use(
      http.post(
        '/v2/incidents/search',
        () =>
          HttpResponse.json({
            items: [
              {
                errorType: 'IO_MAPPING_ERROR',
                errorMessage: 'mapping failed',
                creationTime: new Date(Date.now() - 60_000).toISOString(),
              },
              {
                errorType: 'JOB_NO_RETRIES',
                errorMessage: 'no retries left',
                creationTime: new Date(Date.now() - 120_000).toISOString(),
              },
            ],
          }),
        {once: true},
      ),
    );

    // when
    render(<ActivityFeedWidget config={singleSourceConfig} />, {
      wrapper: Wrapper,
    });

    // then
    expect(await screen.findByText('IO_MAPPING_ERROR')).toBeInTheDocument();
    expect(screen.getByText('JOB_NO_RETRIES')).toBeInTheDocument();
    expect(screen.getByTestId('activity-feed')).toBeInTheDocument();
  });

  it('should render the loading state while data is loading', () => {
    // given — server never responds
    mockServer.use(
      http.post('/v2/incidents/search', async () => {
        await new Promise(() => {});
        return HttpResponse.json({});
      }),
    );

    // when
    render(<ActivityFeedWidget config={singleSourceConfig} />, {
      wrapper: Wrapper,
    });

    // then
    expect(screen.getByTestId('activity-feed-loading')).toBeInTheDocument();
  });

  it('should render the error state on fetch failure', async () => {
    // given
    mockServer.use(
      http.post('/v2/incidents/search', () => HttpResponse.error(), {
        once: true,
      }),
    );

    // when
    render(<ActivityFeedWidget config={singleSourceConfig} />, {
      wrapper: Wrapper,
    });

    // then
    expect(
      await screen.findByTestId('activity-feed-error'),
    ).toBeInTheDocument();
  });
});

// ---------------------------------------------------------------------------
// Multi-source smoke tests
// ---------------------------------------------------------------------------

const multiSourceConfig: WidgetConfig = {
  id: 'af-multi-test',
  type: 'activity-feed',
  title: 'Live activity stream',
  query: {endpoint: '/__notebook_activity_multi__', method: 'GET'},
  activitySources: [
    {
      label: 'Instance started',
      query: {
        endpoint: '/v2/process-instances/search',
        method: 'POST',
        body: {sort: [{field: 'startDate', order: 'DESC'}], page: {limit: 12}},
      },
      titleField: 'processDefinitionId',
      timeField: 'startDate',
      accent: 'info',
    },
    {
      label: 'Incident raised',
      query: {
        endpoint: '/v2/incidents/search',
        method: 'POST',
        body: {
          sort: [{field: 'creationTime', order: 'DESC'}],
          page: {limit: 12},
        },
      },
      titleField: 'errorType',
      subtitleField: 'errorMessage',
      timeField: 'creationTime',
      accent: 'error',
    },
  ],
};

describe('<ActivityFeedWidget /> — multi-source', () => {
  it('should merge items from both sources into one interleaved timeline', async () => {
    // given — two endpoints, each returning one item
    mockServer.use(
      http.post(
        '/v2/process-instances/search',
        () =>
          HttpResponse.json({
            items: [
              {
                processDefinitionId: 'order-process',
                startDate: new Date(Date.now() - 30_000).toISOString(),
              },
            ],
          }),
        {once: true},
      ),
      http.post(
        '/v2/incidents/search',
        () =>
          HttpResponse.json({
            items: [
              {
                errorType: 'JOB_NO_RETRIES',
                errorMessage: 'all retries exhausted',
                creationTime: new Date(Date.now() - 90_000).toISOString(),
              },
            ],
          }),
        {once: true},
      ),
    );

    // when
    render(<ActivityFeedWidget config={multiSourceConfig} />, {
      wrapper: Wrapper,
    });

    // then — items from both sources appear in the single timeline
    expect(await screen.findByText('order-process')).toBeInTheDocument();
    expect(screen.getByText('JOB_NO_RETRIES')).toBeInTheDocument();

    // and source labels are shown
    expect(screen.getByText('Instance started')).toBeInTheDocument();
    expect(screen.getByText('Incident raised')).toBeInTheDocument();

    // and the feed wrapper is present
    expect(screen.getByTestId('activity-feed')).toBeInTheDocument();
  });

  it('should show skeleton rows while sources are loading', () => {
    // given — neither endpoint responds
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
    render(<ActivityFeedWidget config={multiSourceConfig} />, {
      wrapper: Wrapper,
    });

    // then
    expect(screen.getByTestId('activity-feed-loading')).toBeInTheDocument();
  });

  it('should render items from the successful source and show a failed count footnote when one source errors', async () => {
    // given — instances endpoint succeeds, incidents endpoint fails
    mockServer.use(
      http.post(
        '/v2/process-instances/search',
        () =>
          HttpResponse.json({
            items: [
              {
                processDefinitionId: 'payment-process',
                startDate: new Date(Date.now() - 10_000).toISOString(),
              },
            ],
          }),
        {once: true},
      ),
      http.post('/v2/incidents/search', () => HttpResponse.error(), {
        once: true,
      }),
    );

    // when
    render(<ActivityFeedWidget config={multiSourceConfig} />, {
      wrapper: Wrapper,
    });

    // then — item from the successful source is visible
    expect(await screen.findByText('payment-process')).toBeInTheDocument();

    // and a failure footnote is shown
    expect(screen.getByText(/1 source failed/i)).toBeInTheDocument();
  });
});

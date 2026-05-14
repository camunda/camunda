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
import {TableWidget} from './TableWidget';
import type {WidgetConfig} from '../types';

type Props = {children?: React.ReactNode};

const Wrapper = ({children}: Props) => (
  <QueryClientProvider client={getMockQueryClient()}>
    {children}
  </QueryClientProvider>
);

const tableConfig: WidgetConfig = {
  id: 'w-table-test',
  type: 'table',
  title: 'Process Instances',
  query: {
    endpoint: '/v2/process-instances/search',
    method: 'POST',
    body: {},
  },
  columns: ['id', 'state', 'processDefinitionId'],
};

describe('<TableWidget />', () => {
  it('should render a DataTable with the configured columns', async () => {
    // given
    mockServer.use(
      http.post(
        '/v2/process-instances/search',
        () =>
          HttpResponse.json({
            page: {totalItems: 2},
            items: [
              {id: 'pi-1', state: 'ACTIVE', processDefinitionId: 'proc-a'},
              {id: 'pi-2', state: 'COMPLETED', processDefinitionId: 'proc-b'},
            ],
          }),
        {once: true},
      ),
    );

    // when
    render(<TableWidget config={tableConfig} />, {wrapper: Wrapper});

    // then – column headers derived from the columns config
    expect(await screen.findByText('id')).toBeInTheDocument();
    expect(await screen.findByText('state')).toBeInTheDocument();
    expect(await screen.findByText('processDefinitionId')).toBeInTheDocument();

    // and data rows
    expect(await screen.findByText('pi-1')).toBeInTheDocument();
    expect(await screen.findByText('ACTIVE')).toBeInTheDocument();
  });

  it('should render a loading skeleton while data is loading', async () => {
    // given – server never responds
    mockServer.use(
      http.post('/v2/process-instances/search', async () => {
        await new Promise(() => {});
        return HttpResponse.json({});
      }),
    );

    // when
    render(<TableWidget config={tableConfig} />, {wrapper: Wrapper});

    // then
    expect(
      screen.getByRole('status', {hidden: true}) ??
        screen.getByTestId('table-skeleton'),
    ).toBeInTheDocument();
  });

  it('should render an empty state when items array is empty', async () => {
    // given
    mockServer.use(
      http.post(
        '/v2/process-instances/search',
        () =>
          HttpResponse.json({
            page: {totalItems: 0},
            items: [],
          }),
        {once: true},
      ),
    );

    // when
    render(<TableWidget config={tableConfig} />, {wrapper: Wrapper});

    // then
    expect(
      await screen.findByText(/no data|no results|empty|no items/i),
    ).toBeInTheDocument();
  });
});

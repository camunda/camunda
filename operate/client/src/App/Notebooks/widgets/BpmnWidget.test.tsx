/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {render, screen, waitFor} from 'modules/testing-library';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {http, HttpResponse} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {BpmnWidget} from './BpmnWidget';
import type {WidgetConfig} from '../types';

// ---------------------------------------------------------------------------
// Stub the Diagram component — we test data plumbing, not BPMN rendering.
// ---------------------------------------------------------------------------

vi.mock('modules/components/Diagram', () => ({
  Diagram: ({xml, children}: {xml: string; children?: React.ReactNode}) => (
    <div data-testid="diagram-stub">
      <span data-testid="diagram-xml">{xml}</span>
      {children}
    </div>
  ),
}));

// ---------------------------------------------------------------------------
// Test helpers
// ---------------------------------------------------------------------------

type Props = {children?: React.ReactNode};

const Wrapper = ({children}: Props) => (
  <QueryClientProvider client={getMockQueryClient()}>
    {children}
  </QueryClientProvider>
);

const MOCK_XML = '<bpmn:definitions>...</bpmn:definitions>';

const MOCK_STATS = {
  items: [
    {elementId: 'task-a', active: 3, canceled: 0, incidents: 1, completed: 10},
    {elementId: 'task-b', active: 0, canceled: 0, incidents: 0, completed: 5},
  ],
};

function makeBpmnConfig(overrides: Partial<WidgetConfig> = {}): WidgetConfig {
  return {
    id: 'w-bpmn-test',
    type: 'bpmn',
    title: 'Order fulfillment',
    query: {
      endpoint: '/v2/process-definitions/1234567890/xml',
      method: 'GET',
    },
    processDefinitionKey: '1234567890',
    overlay: 'combined',
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('<BpmnWidget />', () => {
  it('should render a loading state while XML is loading', () => {
    // given — XML never responds; suppress unhandled stats request (POST)
    mockServer.use(
      http.get('/v2/process-definitions/1234567890/xml', async () => {
        await new Promise(() => {});
        return new HttpResponse(MOCK_XML);
      }),
      http.post(
        '/v2/process-definitions/1234567890/statistics/element-instances',
        () => HttpResponse.json(MOCK_STATS),
      ),
    );

    // when
    render(<BpmnWidget config={makeBpmnConfig()} />, {wrapper: Wrapper});

    // then
    expect(screen.getByTestId('bpmn-loading')).toBeInTheDocument();
  });

  it('should render the BPMN diagram when XML loads successfully', async () => {
    // given
    mockServer.use(
      http.get(
        '/v2/process-definitions/1234567890/xml',
        () =>
          new HttpResponse(MOCK_XML, {headers: {'Content-Type': 'text/xml'}}),
        {once: true},
      ),
      http.post(
        '/v2/process-definitions/1234567890/statistics/element-instances',
        () => HttpResponse.json(MOCK_STATS),
        {once: true},
      ),
    );

    // when
    render(<BpmnWidget config={makeBpmnConfig()} />, {wrapper: Wrapper});

    // then — title is shown and the diagram stub is rendered
    expect(screen.getByText('Order fulfillment')).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByTestId('diagram-stub')).toBeInTheDocument();
    });
  });

  it('should render an error state when XML fetch fails', async () => {
    // given
    mockServer.use(
      http.get(
        '/v2/process-definitions/1234567890/xml',
        () => new HttpResponse(null, {status: 404}),
        {once: true},
      ),
      http.post(
        '/v2/process-definitions/1234567890/statistics/element-instances',
        () => HttpResponse.json(MOCK_STATS),
      ),
    );

    // when
    render(<BpmnWidget config={makeBpmnConfig()} />, {wrapper: Wrapper});

    // then
    await waitFor(() => {
      expect(screen.getByText(/could not load diagram/i)).toBeInTheDocument();
    });
  });

  it('should not fetch stats when overlay is "none"', async () => {
    // given
    let statsFetched = false;
    mockServer.use(
      http.get(
        '/v2/process-definitions/1234567890/xml',
        () =>
          new HttpResponse(MOCK_XML, {headers: {'Content-Type': 'text/xml'}}),
        {once: true},
      ),
      http.post(
        '/v2/process-definitions/1234567890/statistics/element-instances',
        () => {
          statsFetched = true;
          return HttpResponse.json(MOCK_STATS);
        },
      ),
    );

    // when
    render(<BpmnWidget config={makeBpmnConfig({overlay: 'none'})} />, {
      wrapper: Wrapper,
    });

    // then — diagram renders without stats
    await waitFor(() => {
      expect(screen.getByTestId('diagram-stub')).toBeInTheDocument();
    });
    expect(statsFetched).toBe(false);
  });

  it('should render an empty state when no processDefinitionKey is provided', () => {
    // given
    const config = makeBpmnConfig({processDefinitionKey: undefined});

    // when
    render(<BpmnWidget config={config} />, {wrapper: Wrapper});

    // then
    expect(screen.getByText(/no process definition key/i)).toBeInTheDocument();
  });
});

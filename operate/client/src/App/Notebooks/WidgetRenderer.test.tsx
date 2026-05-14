/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {WidgetRenderer} from './WidgetRenderer';
import type {WidgetConfig} from './types';

// Stub inner widgets so WidgetRenderer tests are isolated from their internals.
vi.mock('./widgets/MetricWidget', () => ({
  MetricWidget: ({config}: {config: WidgetConfig}) => (
    <div data-testid="metric-widget">{config.title}</div>
  ),
}));

vi.mock('./widgets/TableWidget', () => ({
  TableWidget: ({config}: {config: WidgetConfig}) => (
    <div data-testid="table-widget">{config.title}</div>
  ),
}));

const metricConfig: WidgetConfig = {
  id: 'w-metric-1',
  type: 'metric',
  title: 'Active Instances',
  query: {endpoint: '/v2/process-instances/search', method: 'POST'},
  field: 'page.totalItems',
};

const tableConfig: WidgetConfig = {
  id: 'w-table-1',
  type: 'table',
  title: 'Instance Table',
  query: {endpoint: '/v2/process-instances/search', method: 'POST'},
  columns: ['id', 'state'],
};

describe('<WidgetRenderer />', () => {
  it('should render MetricWidget when config.type is "metric"', () => {
    // given
    // when
    render(<WidgetRenderer config={metricConfig} />);

    // then
    expect(screen.getByTestId('metric-widget')).toBeInTheDocument();
    expect(screen.getByText('Active Instances')).toBeInTheDocument();
    expect(screen.queryByTestId('table-widget')).not.toBeInTheDocument();
  });

  it('should render TableWidget when config.type is "table"', () => {
    // given
    // when
    render(<WidgetRenderer config={tableConfig} />);

    // then
    expect(screen.getByTestId('table-widget')).toBeInTheDocument();
    expect(screen.getByText('Instance Table')).toBeInTheDocument();
    expect(screen.queryByTestId('metric-widget')).not.toBeInTheDocument();
  });

  it('should render a fallback for unknown widget types', () => {
    // given – cast to bypass TS so we can exercise the runtime branch
    const unknownConfig = {
      ...metricConfig,
      type: 'unknown-future-type',
    } as unknown as WidgetConfig;

    // when
    render(<WidgetRenderer config={unknownConfig} />);

    // then – must not crash and should show something indicating it is unsupported
    expect(
      screen.getByText(/unsupported|unknown|not supported/i),
    ).toBeInTheDocument();
  });
});

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {useQuery} from '@tanstack/react-query';
import {InlineLoading, Tile} from '@carbon/react';
import {
  SimpleBarChart,
  LineChart,
  DonutChart,
  PieChart,
  StackedBarChart,
  StackedAreaChart,
  MeterChart,
  TreemapChart,
  RadarChart,
} from '@carbon/charts-react';
import '@carbon/charts-react/styles.css';
import {requestWithThrow} from 'modules/request';
import type {WidgetConfig} from '../types';
import {WidgetTitle, WidgetSubtitle, EmptyState} from '../styled';
import {
  buildChartData,
  buildStackedChartData,
  buildTreemapData,
  buildRadarData,
} from './chartUtils';
import {
  SEMANTIC_COLOR_SCALE,
  HARMONIOUS_PALETTE,
  COMMON_CHART_OPTIONS,
  CHART_HEIGHT,
  barOptions,
  lineOptions,
  stackedBarOptions,
  donutOptions,
} from './chartOptions';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

type Props = {
  config: WidgetConfig;
  /** Override the chart height — used by ChartListWidget for compact mode. */
  heightOverride?: string;
};

type ApiResponse = {
  items: Record<string, unknown>[];
  page?: {totalItems: number};
};

// ---------------------------------------------------------------------------
// Local options for chart types not in chartOptions.ts
// ---------------------------------------------------------------------------

function pieOptions(height: string) {
  return {
    ...COMMON_CHART_OPTIONS,
    title: '',
    height,
    resizable: true,
    legend: {position: 'right' as const},
    color: {scale: SEMANTIC_COLOR_SCALE, pairing: {option: 2}},
  };
}

function stackedAreaOptions(groupBy: string, height: string) {
  return {
    ...COMMON_CHART_OPTIONS,
    title: '',
    height,
    axes: {
      left: {mapsTo: 'value', stacked: true},
      bottom: {
        mapsTo: 'group',
        scaleType: 'labels' as const,
        title: groupBy,
      },
    },
    legend: {enabled: true},
    color: {scale: SEMANTIC_COLOR_SCALE, pairing: {option: 2}},
    data: {groupMapsTo: 'key'},
  } as const;
}

function meterOptions(height: string) {
  return {
    ...COMMON_CHART_OPTIONS,
    title: '',
    height,
    meter: {
      proportional: {
        total: undefined,
      },
    },
    color: {scale: SEMANTIC_COLOR_SCALE, pairing: {option: 2}},
  };
}

function treemapOptions(height: string) {
  return {
    ...COMMON_CHART_OPTIONS,
    title: '',
    height,
    color: {
      scale: SEMANTIC_COLOR_SCALE,
      pairing: {option: 2},
      // Provide explicit gradient colors for treemap groups that aren't in
      // the semantic map; Carbon Charts falls back to its gradient scale.
      gradient: {colors: HARMONIOUS_PALETTE},
    },
  };
}

function radarOptions(height: string) {
  return {
    ...COMMON_CHART_OPTIONS,
    title: '',
    height,
    radar: {
      axes: {angle: 'key', value: 'value'},
    },
    legend: {enabled: true},
    color: {
      scale: SEMANTIC_COLOR_SCALE,
      pairing: {option: 2},
    },
  };
}

// ---------------------------------------------------------------------------
// ChartWidget
// ---------------------------------------------------------------------------

const ChartWidget: React.FC<Props> = ({config, heightOverride}) => {
  const {
    title,
    subtitle,
    query,
    chartType = 'bar',
    chartGroupBy = 'state',
    chartValueField,
    chartStackBy = 'state',
  } = config;

  const chartHeight = heightOverride ?? CHART_HEIGHT;

  const {data, status} = useQuery({
    queryKey: ['notebook-widget', config.id, query],
    queryFn: async () => {
      const {response, error} = await requestWithThrow<ApiResponse>({
        url: query.endpoint,
        method: query.method,
        body: query.body,
      });
      if (error) {
        throw error;
      }
      return response;
    },
  });

  if (status === 'pending') {
    return (
      <Tile>
        <WidgetTitle>{title}</WidgetTitle>
        {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
        <InlineLoading
          description="Loading chart…"
          data-testid="chart-loading"
        />
      </Tile>
    );
  }

  if (status === 'error') {
    return (
      <Tile>
        <WidgetTitle>{title}</WidgetTitle>
        {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
        <EmptyState data-testid="chart-error">Could not load chart.</EmptyState>
      </Tile>
    );
  }

  const items = data?.items ?? [];

  if (items.length === 0) {
    return (
      <Tile>
        <WidgetTitle>{title}</WidgetTitle>
        {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
        <EmptyState data-testid="chart-empty">No data available.</EmptyState>
      </Tile>
    );
  }

  if (chartType === 'stacked-bar') {
    const stackedData = buildStackedChartData(
      items,
      chartGroupBy,
      chartStackBy,
    );
    return (
      <Tile>
        <WidgetTitle>{title}</WidgetTitle>
        {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
        <StackedBarChart
          data={stackedData}
          options={stackedBarOptions(chartGroupBy, chartHeight)}
        />
      </Tile>
    );
  }

  if (chartType === 'stacked-area') {
    const stackedData = buildStackedChartData(
      items,
      chartGroupBy,
      chartStackBy,
    );
    return (
      <Tile>
        <WidgetTitle>{title}</WidgetTitle>
        {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
        <StackedAreaChart
          data={stackedData}
          options={stackedAreaOptions(chartGroupBy, chartHeight)}
        />
      </Tile>
    );
  }

  if (chartType === 'treemap') {
    const treeData = buildTreemapData(items, chartGroupBy);
    return (
      <Tile>
        <WidgetTitle>{title}</WidgetTitle>
        {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
        <TreemapChart data={treeData} options={treemapOptions(chartHeight)} />
      </Tile>
    );
  }

  if (chartType === 'radar') {
    const radarData = buildRadarData(items, chartGroupBy, chartStackBy);
    return (
      <Tile>
        <WidgetTitle>{title}</WidgetTitle>
        {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
        <RadarChart data={radarData} options={radarOptions(chartHeight)} />
      </Tile>
    );
  }

  if (chartType === 'meter') {
    const chartData = buildChartData(items, chartGroupBy, chartValueField);
    return (
      <Tile>
        <WidgetTitle>{title}</WidgetTitle>
        {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
        <MeterChart data={chartData} options={meterOptions(chartHeight)} />
      </Tile>
    );
  }

  const chartData = buildChartData(items, chartGroupBy, chartValueField);

  if (chartType === 'donut') {
    const total = chartData.reduce((sum, d) => sum + d.value, 0);
    return (
      <Tile>
        <WidgetTitle>{title}</WidgetTitle>
        {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
        <DonutChart
          data={chartData}
          options={donutOptions(chartGroupBy, chartHeight, total)}
        />
      </Tile>
    );
  }

  if (chartType === 'pie') {
    return (
      <Tile>
        <WidgetTitle>{title}</WidgetTitle>
        {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
        <PieChart data={chartData} options={pieOptions(chartHeight)} />
      </Tile>
    );
  }

  if (chartType === 'line') {
    return (
      <Tile>
        <WidgetTitle>{title}</WidgetTitle>
        {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
        <LineChart
          data={chartData}
          options={lineOptions(chartGroupBy, chartHeight)}
        />
      </Tile>
    );
  }

  // default: bar
  return (
    <Tile>
      <WidgetTitle>{title}</WidgetTitle>
      {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
      <SimpleBarChart
        data={chartData}
        options={barOptions(chartGroupBy, chartHeight)}
      />
    </Tile>
  );
};

export {ChartWidget};

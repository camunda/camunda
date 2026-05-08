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
import {
  WidgetTitle,
  WidgetSubtitle,
  EmptyState,
  CircularChartWrap,
} from '../styled';
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
  // Mirror donut's config. Carbon's pie defaults to `alignment: 'left'` which
  // anchors the disk on the left of the SVG; setting `alignment: 'center'`
  // (matching donutChart's default override) centers the disk while leaving
  // the inherited bottom legend intact. Do NOT touch `pie.labels` here — that
  // toggle has secondary effects on legend rendering in Carbon Charts 1.x.
  return {
    ...COMMON_CHART_OPTIONS,
    title: '',
    height,
    resizable: true,
    pie: {alignment: 'center' as const},
    legend: {position: 'bottom' as const},
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

function meterOptions(height: string, total: number) {
  return {
    ...COMMON_CHART_OPTIONS,
    title: '',
    height,
    meter: {
      proportional: {
        total,
        unit: '',
      },
    },
    color: {scale: SEMANTIC_COLOR_SCALE, pairing: {option: 2}},
  };
}

/**
 * Build a per-leaf color scale for treemap rendering. Carbon Charts uses
 * `color.scale` as a `name → color` map for the treemap's children. Unknown
 * names fall back to a single default color — that's why our shared
 * SEMANTIC_COLOR_SCALE produced uniform blue rectangles for job types like
 * 'validate' / 'charge' that aren't in the semantic dictionary.
 *
 * Cycle through HARMONIOUS_PALETTE so every distinct leaf name gets its
 * own distinct color.
 */
function treemapColorScale(names: string[]): Record<string, string> {
  const scale: Record<string, string> = {};
  for (let i = 0; i < names.length; i++) {
    scale[names[i]!] = HARMONIOUS_PALETTE[i % HARMONIOUS_PALETTE.length]!;
  }
  return scale;
}

function treemapOptions(height: string, colorScale?: Record<string, string>) {
  return {
    ...COMMON_CHART_OPTIONS,
    title: '',
    height,
    // Caller-provided per-name palette (built from leaf names in
    // ChartWidget). See `treemapColorScale()` below for why we don't share
    // SEMANTIC_COLOR_SCALE here.
    color: colorScale != null ? {scale: colorScale} : undefined,
  };
}

function radarOptions(height: string) {
  return {
    ...COMMON_CHART_OPTIONS,
    title: '',
    height,
    radar: {
      axes: {angle: 'key', value: 'value'},
      // Carbon Charts defaults radar.alignment to 'left' (per the source's
      // radarChart defaults), which anchors the polygon at the left edge of
      // the SVG and leaves the right two thirds of the wide tile empty.
      // Centering puts the polygon in the middle of the tile.
      alignment: 'center' as const,
    },
    legend: {enabled: true, position: 'right' as const},
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

  // Radar gets the full HERO tier (480px tile) so the polygon can render
  // square; bump its internal chart height to fill the tile, otherwise it
  // renders as a thin strip at the top of the empty area.
  const chartHeight =
    heightOverride ?? (chartType === 'radar' ? '420px' : CHART_HEIGHT);

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
    // Cap distinct rectangles so the legend doesn't dominate the tile when
    // a real cluster has 30+ process definitions. buildTreemapData already
    // returns roots sorted by value descending, so slicing keeps the
    // largest groups; small ones become a single "Other" bucket.
    const TREEMAP_MAX_GROUPS = 8;
    const allRoots = buildTreemapData(items, chartGroupBy);
    let treeData = allRoots;
    if (allRoots.length > TREEMAP_MAX_GROUPS) {
      const top = allRoots.slice(0, TREEMAP_MAX_GROUPS - 1);
      const tail = allRoots.slice(TREEMAP_MAX_GROUPS - 1);
      const tailValue = tail.reduce(
        (sum, r) => sum + (r.children?.[0]?.value ?? 0),
        0,
      );
      treeData = [
        ...top,
        {name: 'Other', children: [{name: 'Other', value: tailValue}]},
      ];
    }
    // Each root has one child whose name matches the root's name, so the
    // root names give us the full per-leaf color list.
    const colorScale = treemapColorScale(treeData.map((r) => r.name));
    return (
      <Tile>
        <WidgetTitle>{title}</WidgetTitle>
        {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
        <TreemapChart
          data={treeData}
          options={treemapOptions(chartHeight, colorScale)}
        />
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
    // Carbon's proportional meter needs an explicit total to compute segment
    // widths; without it the bar renders empty. Use the sum of all values as
    // 100% — gives a proper "share of total" stacked bar.
    const meterTotal = chartData.reduce((sum, d) => sum + d.value, 0) || 1;
    return (
      <Tile>
        <WidgetTitle>{title}</WidgetTitle>
        {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
        <MeterChart
          data={chartData}
          options={meterOptions(chartHeight, meterTotal)}
        />
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
        <CircularChartWrap>
          <DonutChart
            data={chartData}
            options={donutOptions(chartGroupBy, chartHeight, total)}
          />
        </CircularChartWrap>
      </Tile>
    );
  }

  if (chartType === 'pie') {
    return (
      <Tile>
        <WidgetTitle>{title}</WidgetTitle>
        {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
        <CircularChartWrap>
          <PieChart data={chartData} options={pieOptions(chartHeight)} />
        </CircularChartWrap>
      </Tile>
    );
  }

  if (chartType === 'line') {
    // Special case: when the preset asks to group by a synthetic time bucket
    // ("startHour"/"endHour") the field doesn't exist on the response — we
    // derive it client-side from the corresponding date field. This lets a
    // line chart show real throughput-over-time without needing a server-
    // side aggregation endpoint.
    let lineData = chartData;
    if (chartGroupBy === 'startHour' || chartGroupBy === 'endHour') {
      const dateField = chartGroupBy === 'startHour' ? 'startDate' : 'endDate';
      // Bucket items by HH:00 of their date field, then ensure all 24 hours
      // exist (zero-filled) so the line always has a contiguous x-axis.
      const counts = new Map<string, number>();
      for (let h = 0; h < 24; h++) {
        counts.set(`${String(h).padStart(2, '0')}:00`, 0);
      }
      for (const item of items) {
        const raw = item[dateField];
        if (raw == null) {
          continue;
        }
        const t = new Date(raw as string);
        if (isNaN(t.getTime())) {
          continue;
        }
        const key = `${String(t.getUTCHours()).padStart(2, '0')}:00`;
        counts.set(key, (counts.get(key) ?? 0) + 1);
      }
      lineData = Array.from(counts.entries()).map(([group, value]) => ({
        group,
        value,
      }));
    }
    return (
      <Tile>
        <WidgetTitle>{title}</WidgetTitle>
        {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
        <LineChart
          data={lineData}
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

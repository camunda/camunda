/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {useQuery} from '@tanstack/react-query';
import {SkeletonText, Tile} from '@carbon/react';
import {requestWithThrow} from 'modules/request';
import type {WidgetConfig, KpiItem} from '../types';
import {
  WidgetTitle,
  WidgetSubtitle,
  MetricCaption,
  MetricValue,
  KpiGrid,
  KpiCell,
} from '../styled';

type Props = {
  config: WidgetConfig;
};

// ---------------------------------------------------------------------------
// Path traversal helper (mirrors MetricWidget)
// ---------------------------------------------------------------------------

function getByPath(obj: unknown, path: string): unknown {
  return path
    .split('.')
    .reduce(
      (acc, key) =>
        acc != null && typeof acc === 'object'
          ? (acc as Record<string, unknown>)[key]
          : undefined,
      obj,
    );
}

// ---------------------------------------------------------------------------
// Single KPI cell — runs its own useQuery so fetches are parallel.
// React Query deduplicates by queryKey when multiple cells share an endpoint.
// ---------------------------------------------------------------------------

type KpiCellProps = {
  kpi: KpiItem;
  widgetId: string;
  index: number;
};

const KpiCellWidget: React.FC<KpiCellProps> = ({kpi, widgetId, index}) => {
  const field = kpi.field ?? 'page.totalItems';
  const accent = kpi.accent ?? 'info';

  const {data, status} = useQuery({
    queryKey: [
      'notebook-kpi',
      widgetId,
      index,
      kpi.query.endpoint,
      kpi.query.method,
      kpi.query.body,
    ],
    queryFn: async () => {
      const {response, error} = await requestWithThrow<Record<string, unknown>>(
        {
          url: kpi.query.endpoint,
          method: kpi.query.method,
          body: kpi.query.body,
        },
      );
      if (error) {
        throw error;
      }
      return response;
    },
  });

  if (status === 'pending') {
    return (
      <KpiCell $accent={accent} data-testid="kpi-cell-loading">
        <MetricCaption>{kpi.label}</MetricCaption>
        <SkeletonText heading width="60px" />
      </KpiCell>
    );
  }

  if (status === 'error') {
    return (
      <KpiCell $accent={accent} data-testid="kpi-cell-error">
        <MetricCaption>{kpi.label}</MetricCaption>
        <MetricValue>—</MetricValue>
      </KpiCell>
    );
  }

  const rawValue = getByPath(data, field);
  const displayValue =
    typeof rawValue === 'number'
      ? rawValue.toLocaleString()
      : String(rawValue ?? '—');

  return (
    <KpiCell $accent={accent} data-testid="kpi-cell">
      <MetricCaption>{kpi.label}</MetricCaption>
      <MetricValue>{displayValue}</MetricValue>
    </KpiCell>
  );
};

// ---------------------------------------------------------------------------
// KpiWidget — the hero tile. One Tile, N cells in a CSS grid.
// ---------------------------------------------------------------------------

const KpiWidget: React.FC<Props> = ({config}) => {
  const {title, subtitle, kpis = []} = config;

  if (kpis.length === 0) {
    return (
      <Tile>
        <WidgetTitle>{title}</WidgetTitle>
        {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
        <p>No KPI items configured.</p>
      </Tile>
    );
  }

  return (
    <Tile>
      <WidgetTitle>{title}</WidgetTitle>
      {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
      <KpiGrid data-testid="kpi-grid">
        {kpis.map((kpi, i) => (
          <KpiCellWidget
            key={`${config.id}-kpi-${i}`}
            kpi={kpi}
            widgetId={config.id}
            index={i}
          />
        ))}
      </KpiGrid>
    </Tile>
  );
};

export {KpiWidget};

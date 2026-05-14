/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {useQuery} from '@tanstack/react-query';
import {SkeletonText} from '@carbon/react';
import {requestWithThrow} from 'modules/request';
import type {WidgetConfig} from '../types';
import {
  MetricTile,
  MetricCaption,
  MetricValue,
  MetricSubvalue,
} from '../styled';

type Props = {
  config: WidgetConfig;
};

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

function formatAgeFrom(value: unknown): string {
  if (value == null) {
    return '—';
  }
  const t = new Date(value as string).getTime();
  if (isNaN(t)) {
    return '—';
  }
  const ms = Math.max(0, Date.now() - t);
  return formatDurationMs(ms);
}

function formatDurationMs(ms: number): string {
  if (!isFinite(ms) || ms < 0) {
    return '—';
  }
  const sec = Math.floor(ms / 1000);
  const min = Math.floor(sec / 60);
  const hr = Math.floor(min / 60);
  const day = Math.floor(hr / 24);
  if (day > 0) {
    return `${day}d ${hr % 24}h`;
  }
  if (hr > 0) {
    return `${hr}h ${min % 60}m`;
  }
  if (min > 0) {
    return `${min}m`;
  }
  return `${sec}s`;
}

function formatPercent(value: unknown): string {
  if (typeof value !== 'number' || !isFinite(value)) {
    return '—';
  }
  const pct = value <= 1 ? value * 100 : value;
  return `${pct.toFixed(pct >= 10 ? 0 : 1)}%`;
}

const MetricWidget: React.FC<Props> = ({config}) => {
  const {
    title,
    query,
    field = 'page.totalItems',
    accent = 'info',
    metricFormat = 'count',
    metricSubvalueField,
  } = config;

  const {data, status} = useQuery({
    queryKey: ['notebook-widget', config.id, query],
    queryFn: async () => {
      const {response, error} = await requestWithThrow<Record<string, unknown>>(
        {
          url: query.endpoint,
          method: query.method,
          body: query.body,
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
      <MetricTile $accent={accent}>
        <MetricCaption>{title}</MetricCaption>
        <span role="status" data-testid="metric-skeleton">
          <SkeletonText heading width="80px" />
        </span>
      </MetricTile>
    );
  }

  if (status === 'error') {
    return (
      <MetricTile $accent={accent}>
        <MetricCaption>{title}</MetricCaption>
        <MetricValue>—</MetricValue>
        <MetricSubvalue>Could not load data</MetricSubvalue>
      </MetricTile>
    );
  }

  const rawValue = getByPath(data, field);
  let displayValue: string;
  switch (metricFormat) {
    case 'age-from':
      displayValue = formatAgeFrom(rawValue);
      break;
    case 'duration-ms':
      displayValue =
        typeof rawValue === 'number' ? formatDurationMs(rawValue) : '—';
      break;
    case 'percent':
      displayValue = formatPercent(rawValue);
      break;
    default:
      displayValue =
        typeof rawValue === 'number'
          ? rawValue.toLocaleString()
          : String(rawValue ?? '—');
  }

  const subvalueRaw =
    metricSubvalueField != null ? getByPath(data, metricSubvalueField) : null;
  const subvalue =
    subvalueRaw != null && subvalueRaw !== '' ? String(subvalueRaw) : null;

  return (
    <MetricTile $accent={accent}>
      <MetricCaption>{title}</MetricCaption>
      <MetricValue>{displayValue}</MetricValue>
      {subvalue != null && <MetricSubvalue>{subvalue}</MetricSubvalue>}
    </MetricTile>
  );
};

export {MetricWidget};

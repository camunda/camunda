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
  TrendSparklineArea,
} from '../styled';
import {Sparkline} from './Sparkline';

// ---------------------------------------------------------------------------
// Time bucket helpers
// ---------------------------------------------------------------------------

/**
 * Parse a bucket span string like '1h', '6h', '24h', '7d' into milliseconds.
 * Falls back to 86_400_000 (24h) for unrecognised strings.
 */
function parseBucketSpanMs(span: string): number {
  const match = /^(\d+)(h|d)$/.exec(span.toLowerCase());
  if (!match) {
    return 86_400_000;
  }
  const value = parseInt(match[1] as string, 10);
  const unit = match[2];
  if (unit === 'h') {
    return value * 3_600_000;
  }
  if (unit === 'd') {
    return value * 86_400_000;
  }
  return 86_400_000;
}

type TimeWindow = {start: Date; end: Date};

/**
 * Generate `count` non-overlapping time windows of `spanMs` ending at `now`.
 * Returns them in chronological order (oldest first, most recent last).
 */
function buildTimeWindows(
  count: number,
  spanMs: number,
  now: Date,
): TimeWindow[] {
  const windows: TimeWindow[] = [];
  const nowMs = now.getTime();
  for (let i = count - 1; i >= 0; i--) {
    const endMs = nowMs - i * spanMs;
    const startMs = endMs - spanMs;
    windows.push({start: new Date(startMs), end: new Date(endMs)});
  }
  return windows;
}

// ---------------------------------------------------------------------------
// Single-bucket query component
// ---------------------------------------------------------------------------

type BucketQueryProps = {
  widgetId: string;
  bucketIndex: number;
  endpoint: string;
  method: 'GET' | 'POST';
  baseBody: unknown;
  dateField: string;
  window: TimeWindow;
};

type BucketResult = {
  count: number | null;
  isLoading: boolean;
  isError: boolean;
};

function useBucketQuery({
  widgetId,
  bucketIndex,
  endpoint,
  method,
  baseBody,
  dateField,
  window,
}: BucketQueryProps): BucketResult {
  const isoStart = window.start.toISOString();
  const isoEnd = window.end.toISOString();

  // Merge the date-range filter into the base body using the V2 Mongo-style
  // operator format confirmed in the API type definitions:
  //   startDate: {$gte: isoStart, $lt: isoEnd}
  const body = React.useMemo(
    () => ({
      ...(baseBody != null && typeof baseBody === 'object' ? baseBody : {}),
      filter: {
        ...((baseBody as {filter?: Record<string, unknown>} | null)?.filter ??
          {}),
        [dateField]: {$gte: isoStart, $lt: isoEnd},
      },
    }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [isoStart, isoEnd, dateField, JSON.stringify(baseBody)],
  );

  const {data, status} = useQuery({
    queryKey: [
      'notebook-trend-bucket',
      widgetId,
      bucketIndex,
      endpoint,
      method,
      dateField,
      isoStart,
      isoEnd,
      body,
    ],
    queryFn: async () => {
      const {response, error} = await requestWithThrow<Record<string, unknown>>(
        {
          url: endpoint,
          method,
          body,
        },
      );
      if (error) {
        throw error;
      }
      return response;
    },
  });

  if (status === 'pending') {
    return {count: null, isLoading: true, isError: false};
  }
  if (status === 'error') {
    return {count: null, isLoading: false, isError: true};
  }

  // Navigate page.totalItems
  const pageData = data?.['page'] as Record<string, unknown> | undefined;
  const totalItems = pageData?.['totalItems'];
  const count = typeof totalItems === 'number' ? totalItems : null;
  return {count, isLoading: false, isError: false};
}

// ---------------------------------------------------------------------------
// TrendWidget — the hero component
// ---------------------------------------------------------------------------

type Props = {
  config: WidgetConfig;
};

/**
 * BucketQueryRunner is a helper that calls useBucketQuery (a hook) per bucket
 * and surfaces the result via a render-prop callback. This preserves the
 * Rules of Hooks (hooks can only be called at the top level of a component)
 * by putting each bucket query inside its own component instance.
 */
type BucketQueryRunnerProps = BucketQueryProps & {
  onResult: (index: number, result: BucketResult) => void;
};

const BucketQueryRunner: React.FC<BucketQueryRunnerProps> = ({
  onResult,
  ...props
}) => {
  const result = useBucketQuery(props);
  const {bucketIndex} = props;
  const {count, isLoading, isError} = result;
  React.useEffect(() => {
    onResult(bucketIndex, {count, isLoading, isError});
  }, [bucketIndex, count, isLoading, isError, onResult]);
  return null;
};

/**
 * TrendWidget fires N parallel queries (one per time bucket) and renders the
 * per-bucket counts as a Sparkline. Date-range filters use the V2 Mongo-style
 * operator format: `{[dateField]: {$gte: isoStart, $lt: isoEnd}}`.
 */
const TrendWidget: React.FC<Props> = ({config}) => {
  const {
    title,
    subtitle,
    query,
    trendDateField = 'startDate',
    trendBuckets = 7,
    trendBucketSpan = '24h',
    trendAccent = 'info',
  } = config;

  const spanMs = parseBucketSpanMs(trendBucketSpan);
  const now = React.useMemo(() => new Date(), []);
  const windows = React.useMemo(
    () => buildTimeWindows(trendBuckets, spanMs, now),
    [trendBuckets, spanMs, now],
  );

  // Bucket results indexed by bucket position
  const [results, setResults] = React.useState<BucketResult[]>(() =>
    windows.map(() => ({count: null, isLoading: true, isError: false})),
  );

  const handleResult = React.useCallback(
    (index: number, result: BucketResult) => {
      setResults((prev) => {
        const next = [...prev];
        next[index] = result;
        return next;
      });
    },
    [],
  );

  const anyLoading = results.some((r) => r.isLoading);
  const allError = results.every((r) => r.isError);

  // Build sparkline points — use 0 for buckets that errored (partial tolerance)
  const points = results.map((r) => (r.count != null ? r.count : 0));

  const lastCount = results[results.length - 1]?.count;

  // Sized identically to a metric tile: caption + value + small sparkline.
  // 3 children inside MetricTile's flex column — adds up to ~110px content,
  // floors to MetricTile's min-height of 132px exactly. Drops the prior
  // "Last 7 days · ending …" footer to keep the tile's natural height in
  // sync with sibling metric tiles in the same row.
  return (
    <MetricTile $accent={trendAccent}>
      {/* Render one hidden query runner per bucket — each calls its own useQuery */}
      {windows.map((win, i) => (
        <BucketQueryRunner
          key={`${config.id}-bucket-${i}`}
          widgetId={config.id}
          bucketIndex={i}
          endpoint={query.endpoint}
          method={query.method}
          baseBody={query.body}
          dateField={trendDateField}
          window={win}
          onResult={handleResult}
        />
      ))}

      <MetricCaption>{subtitle ?? title}</MetricCaption>

      {anyLoading && <SkeletonText heading width="80px" />}

      {!anyLoading && allError && <MetricValue>—</MetricValue>}

      {!anyLoading && !allError && (
        <>
          <MetricValue>
            {lastCount != null ? lastCount.toLocaleString() : '—'}
          </MetricValue>
          <TrendSparklineArea>
            <Sparkline
              points={points}
              color={trendAccent}
              width={180}
              height={24}
            />
          </TrendSparklineArea>
        </>
      )}
    </MetricTile>
  );
};

export {TrendWidget};

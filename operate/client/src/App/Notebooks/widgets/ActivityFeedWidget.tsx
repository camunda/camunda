/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {useQueries, useQuery} from '@tanstack/react-query';
import {SkeletonText, Tile} from '@carbon/react';
import {requestWithThrow} from 'modules/request';
import type {WidgetConfig, ActivitySource} from '../types';
import {
  WidgetTitle,
  WidgetSubtitle,
  EmptyState,
  ActivityFeed,
  ActivityRow,
  ActivityDot,
  ActivityContent,
  ActivityTitle,
  ActivitySubtitle,
  ActivityTime,
  ACCENT_COLOR,
} from '../styled';

type Props = {
  config: WidgetConfig;
  /** When true the feed uses internal scroll (hero variant). */
  isHero?: boolean;
};

type ApiResponse = {
  items: Record<string, unknown>[];
};

const MAX_ITEMS_TALL = 12;
const MAX_ITEMS_HERO = 20;

/**
 * Returns a human-readable relative time string ("2 minutes ago", "1 hour ago").
 * Falls back to the raw value string if parsing fails.
 */
function relativeTime(value: unknown): string {
  if (value == null) {
    return '';
  }
  const date = new Date(value as string | number);
  if (isNaN(date.getTime())) {
    return String(value);
  }

  const diffMs = Date.now() - date.getTime();
  const diffSec = Math.floor(diffMs / 1000);
  if (diffSec < 60) {
    return `${Math.max(0, diffSec)}s ago`;
  }
  const diffMin = Math.floor(diffSec / 60);
  if (diffMin < 60) {
    return `${diffMin}m ago`;
  }
  const diffHr = Math.floor(diffMin / 60);
  if (diffHr < 24) {
    return `${diffHr}h ago`;
  }
  const diffDay = Math.floor(diffHr / 24);
  return `${diffDay}d ago`;
}

/**
 * Derive a dot color from a known field value. Falls back to info color.
 */
function kindToColor(kind: unknown): string {
  if (kind == null) {
    return ACCENT_COLOR['info']!;
  }
  const s = String(kind).toUpperCase();
  if (s === 'INCIDENT' || s === 'ERROR' || s === 'FAILED') {
    return ACCENT_COLOR['error']!;
  }
  if (s === 'COMPLETED' || s === 'SUCCESS') {
    return ACCENT_COLOR['success']!;
  }
  if (s === 'WARNING') {
    return ACCENT_COLOR['warning']!;
  }
  return ACCENT_COLOR['info']!;
}

// ---------------------------------------------------------------------------
// Normalized feed entry used for the merged timeline
// ---------------------------------------------------------------------------

type FeedEntry = {
  sourceLabel: string;
  title: string;
  subtitle?: string;
  time: unknown;
  timeMs: number;
  dotColor: string;
};

// ---------------------------------------------------------------------------
// Multi-source feed sub-component
// ---------------------------------------------------------------------------

type MultiSourceFeedProps = {
  title: string;
  subtitle?: string;
  sources: ActivitySource[];
  isHero?: boolean;
};

const MultiSourceFeed: React.FC<MultiSourceFeedProps> = ({
  title,
  subtitle,
  sources,
  isHero = false,
}) => {
  const results = useQueries({
    queries: sources.map((source) => ({
      queryKey: [
        'notebook-activity-feed-multi',
        source.label,
        source.query,
      ] as const,
      queryFn: async () => {
        const {response, error} = await requestWithThrow<ApiResponse>({
          url: source.query.endpoint,
          method: source.query.method,
          body: source.query.body,
        });
        if (error) {
          throw error;
        }
        return response;
      },
    })),
  });

  const anyPending = results.some((r) => r.status === 'pending');
  const failedCount = results.filter((r) => r.status === 'error').length;

  if (anyPending) {
    return (
      <Tile>
        <WidgetTitle>{title}</WidgetTitle>
        {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
        <ActivityFeed data-testid="activity-feed-loading">
          {Array.from({length: 6}).map((_, i) => (
            <ActivityRow key={i}>
              <ActivityDot />
              <ActivityContent>
                <SkeletonText width="60%" />
                <SkeletonText width="40%" />
              </ActivityContent>
            </ActivityRow>
          ))}
        </ActivityFeed>
      </Tile>
    );
  }

  // Merge all successful results into one chronological list
  const entries: FeedEntry[] = [];

  sources.forEach((source, idx) => {
    const result = results[idx];
    if (result?.status !== 'success') {
      return;
    }
    const items = result.data?.items ?? [];
    const dotColor =
      source.accent != null
        ? (ACCENT_COLOR[source.accent] ?? ACCENT_COLOR['info']!)
        : ACCENT_COLOR['info']!;

    for (const item of items) {
      const timeVal = item[source.timeField];
      const timeMs =
        timeVal != null ? new Date(timeVal as string).getTime() : 0;
      const titleVal = item[source.titleField];
      const subtitleVal =
        source.subtitleField != null ? item[source.subtitleField] : undefined;

      entries.push({
        sourceLabel: source.label,
        title: titleVal != null ? String(titleVal) : '—',
        subtitle: subtitleVal != null ? String(subtitleVal) : undefined,
        time: timeVal,
        timeMs: isNaN(timeMs) ? 0 : timeMs,
        dotColor,
      });
    }
  });

  const maxItems = isHero ? MAX_ITEMS_HERO : MAX_ITEMS_TALL;

  // Sort newest-first, take top N
  const sorted = entries.sort((a, b) => b.timeMs - a.timeMs).slice(0, maxItems);

  if (sorted.length === 0) {
    return (
      <Tile>
        <WidgetTitle>{title}</WidgetTitle>
        {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
        <EmptyState data-testid="activity-feed-empty">
          No recent activity.
        </EmptyState>
        {failedCount > 0 && (
          <EmptyState>
            ({failedCount} source{failedCount > 1 ? 's' : ''} failed)
          </EmptyState>
        )}
      </Tile>
    );
  }

  return (
    <Tile>
      <WidgetTitle>{title}</WidgetTitle>
      {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
      <ActivityFeed $scrollable={isHero} data-testid="activity-feed">
        {sorted.map((entry, i) => (
          <ActivityRow key={i}>
            <ActivityDot $color={entry.dotColor} />
            <ActivityContent>
              <ActivitySubtitle>{entry.sourceLabel}</ActivitySubtitle>
              <ActivityTitle>{entry.title}</ActivityTitle>
              {entry.subtitle != null && (
                <ActivitySubtitle>{entry.subtitle}</ActivitySubtitle>
              )}
              <ActivityTime>{relativeTime(entry.time)}</ActivityTime>
            </ActivityContent>
          </ActivityRow>
        ))}
      </ActivityFeed>
      {failedCount > 0 && (
        <EmptyState>
          ({failedCount} source{failedCount > 1 ? 's' : ''} failed)
        </EmptyState>
      )}
    </Tile>
  );
};

// ---------------------------------------------------------------------------
// Single-source feed (original behaviour, kept for backwards compat)
// ---------------------------------------------------------------------------

type SingleSourceFeedProps = {
  config: WidgetConfig;
  isHero?: boolean;
};

const SingleSourceFeed: React.FC<SingleSourceFeedProps> = ({
  config,
  isHero = false,
}) => {
  const {
    title,
    subtitle,
    query,
    activityTitleField = 'errorType',
    activitySubtitleField = 'errorMessage',
    activityTimeField = 'creationTime',
    activityKindField,
  } = config;

  const {data, status} = useQuery({
    queryKey: ['notebook-activity-feed', config.id, query],
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
        <ActivityFeed data-testid="activity-feed-loading">
          {Array.from({length: 6}).map((_, i) => (
            <ActivityRow key={i}>
              <ActivityDot />
              <ActivityContent>
                <SkeletonText width="60%" />
                <SkeletonText width="40%" />
              </ActivityContent>
            </ActivityRow>
          ))}
        </ActivityFeed>
      </Tile>
    );
  }

  if (status === 'error') {
    return (
      <Tile>
        <WidgetTitle>{title}</WidgetTitle>
        {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
        <EmptyState data-testid="activity-feed-error">
          Could not load activity.
        </EmptyState>
      </Tile>
    );
  }

  const rawItems = data?.items ?? [];

  // Sort by time field descending (newest first), then limit.
  const sorted = [...rawItems]
    .sort((a, b) => {
      const ta = a[activityTimeField];
      const tb = b[activityTimeField];
      const da = ta != null ? new Date(ta as string).getTime() : 0;
      const db = tb != null ? new Date(tb as string).getTime() : 0;
      return db - da;
    })
    .slice(0, isHero ? MAX_ITEMS_HERO : MAX_ITEMS_TALL);

  if (sorted.length === 0) {
    return (
      <Tile>
        <WidgetTitle>{title}</WidgetTitle>
        {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
        <EmptyState data-testid="activity-feed-empty">
          No recent activity.
        </EmptyState>
      </Tile>
    );
  }

  return (
    <Tile>
      <WidgetTitle>{title}</WidgetTitle>
      {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
      <ActivityFeed $scrollable={isHero} data-testid="activity-feed">
        {sorted.map((item, i) => {
          const titleVal = item[activityTitleField];
          const subtitleVal = item[activitySubtitleField];
          const timeVal = item[activityTimeField];
          const kindVal = activityKindField
            ? item[activityKindField]
            : titleVal;

          const dotColor = kindToColor(kindVal);

          return (
            <ActivityRow key={i}>
              <ActivityDot $color={dotColor} />
              <ActivityContent>
                <ActivityTitle>
                  {titleVal != null ? String(titleVal) : '—'}
                </ActivityTitle>
                {subtitleVal != null && (
                  <ActivitySubtitle>{String(subtitleVal)}</ActivitySubtitle>
                )}
                <ActivityTime>{relativeTime(timeVal)}</ActivityTime>
              </ActivityContent>
            </ActivityRow>
          );
        })}
      </ActivityFeed>
    </Tile>
  );
};

// ---------------------------------------------------------------------------
// Public component — routes to multi-source or single-source
// ---------------------------------------------------------------------------

const ActivityFeedWidget: React.FC<Props> = ({config, isHero}) => {
  const {activitySources, title, subtitle, activityFeedSize} = config;
  const heroMode = isHero ?? activityFeedSize === 'hero';

  if (activitySources != null && activitySources.length > 0) {
    return (
      <MultiSourceFeed
        title={title}
        subtitle={subtitle}
        sources={activitySources}
        isHero={heroMode}
      />
    );
  }

  return <SingleSourceFeed config={config} isHero={heroMode} />;
};

export {ActivityFeedWidget};

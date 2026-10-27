/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Duration, sub} from 'date-fns';

/**
 * Pure helpers backing the period-over-period comparison ("delta badge") on KPI
 * number tiles. Kept free of React/rendering so each piece is unit-testable in
 * isolation: baseline-filter construction, delta semantics, value formatting,
 * value-unit derivation and the period label.
 */

type RollingPeriod = {value: number; unit: string};

/** ms / % / plain — the unit the KPI value (and therefore the delta) is in. */
export type ValueUnit = 'ms' | '%' | '';

/** Whether the change is good, bad or unchanged for this KPI. */
export type DeltaDirection = 'good' | 'bad' | 'neutral';

/** Direction in which a change is considered an improvement. */
export type DeltaGoodDirection = 'up' | 'down';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type ComparisonFilter = Array<{type: string; filterLevel?: string; data?: any}>;

const INSTANCE_END_DATE = 'instanceEndDate';

/**
 * Maps a rolling preset (value + unit) to a comparison-label translation key.
 * The dashboard only offers 7d / 30d / 3m / 6m / 12m, so every selectable preset
 * is covered here.
 */
const LABEL_KEY_BY_PRESET: Record<string, string> = {
  '7-days': 'wow',
  '30-days': 'mom',
  '3-months': 'qoq',
  '6-months': 'hoh',
  '12-months': 'yoy',
};

/** Returns the rolling end-date period of the filter, or null if there is none. */
export function getRollingPeriod(
  filter: ComparisonFilter | undefined | null
): RollingPeriod | null {
  const rollingDateFilter = filter?.find(
    (f) => f.type === INSTANCE_END_DATE && f.data?.type === 'rolling'
  );
  return rollingDateFilter ? rollingDateFilter.data.start : null;
}

/**
 * Builds the filter for the equal-length window immediately preceding the
 * current rolling window. Returns null when there is no rolling end-date filter
 * to mirror, so callers can skip the fetch and show "no baseline" rather than
 * comparing the current window against itself.
 *
 * The boundaries are computed calendar-aware (date-fns) so the prior window
 * spans the same real duration the backend evaluates for the current one — a
 * "last 3 months" window and its baseline both honour calendar month lengths.
 */
export function buildBaselineFilter(
  filter: ComparisonFilter | undefined | null
): ComparisonFilter | null {
  const period = getRollingPeriod(filter);
  if (!period) {
    return null;
  }
  const now = new Date();
  const priorEnd = sub(now, toDuration(period.unit, period.value));
  const priorStart = sub(now, toDuration(period.unit, period.value * 2));
  return [
    ...(filter ?? []).filter((f) => f.type !== INSTANCE_END_DATE),
    {
      type: INSTANCE_END_DATE,
      filterLevel: 'instance',
      data: {
        type: 'fixed',
        start: priorStart.toISOString(),
        end: priorEnd.toISOString(),
        includeUndefined: false,
        excludeUndefined: false,
      },
    },
  ];
}

/** Classifies the change against the prior value for the given good-direction. */
export function classifyDelta(
  currentValue: number,
  priorValue: number,
  goodDirection: DeltaGoodDirection
): {delta: number; direction: DeltaDirection} {
  const delta = currentValue - priorValue;
  if (delta === 0) {
    return {delta, direction: 'neutral'};
  }
  const isPositive = delta > 0;
  const isGood =
    (goodDirection === 'up' && isPositive) || (goodDirection === 'down' && !isPositive);
  return {delta, direction: isGood ? 'good' : 'bad'};
}

/** Formats a signed delta for display, in the KPI value's own unit. */
export function formatDelta(delta: number, unit: ValueUnit): string {
  const sign = delta >= 0 ? '+' : '-';
  const absDelta = Math.abs(delta);
  if (unit === 'ms') {
    return absDelta >= 1000
      ? `${sign}${(absDelta / 1000).toFixed(1)}s`
      : `${sign}${Math.round(absDelta)}ms`;
  }
  if (unit === '%') {
    return `${sign}${absDelta.toFixed(absDelta < 1 ? 1 : 0)}%`;
  }
  return `${sign}${Math.round(absDelta)}`;
}

/** Derives the value unit (ms / % / plain) from the report's view property. */
export function propertyToValueUnit(property: string | undefined): ValueUnit {
  if (property === 'duration') {
    return 'ms';
  }
  if (property === 'percentage') {
    return '%';
  }
  return '';
}

/**
 * Translation key (under `agenticControlPlane.comparison`) for the period label,
 * derived from the rolling preset, or null when the period is unknown so callers
 * can omit the label rather than render a misleading one.
 */
export function comparisonLabelKey(period: RollingPeriod | null): string | null {
  if (!period) {
    return null;
  }
  return LABEL_KEY_BY_PRESET[`${period.value}-${period.unit}`] ?? null;
}

function toDuration(unit: string, value: number): Duration {
  if (unit === 'quarters') {
    return {months: value * 3};
  }
  return {[unit]: value};
}

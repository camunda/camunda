/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  buildBaselineFilter,
  classifyDelta,
  comparisonLabelKey,
  formatDelta,
  getRollingPeriod,
  propertyToValueUnit,
} from './comparison';

const rollingFilter = (value: number, unit: string) => ({
  type: 'instanceEndDate',
  filterLevel: 'instance',
  data: {type: 'rolling', start: {value, unit}, end: null},
});

// [year, month, day] in UTC – timezone/DST-robust way to assert the calendar
// date of an ISO instant without depending on the runner's local offset.
const asUTCDate = (iso: string): [number, number, number] => {
  const date = new Date(iso);
  return [date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()];
};

describe('getRollingPeriod', () => {
  it('should return the rolling end-date period', () => {
    // when
    const period = getRollingPeriod([rollingFilter(30, 'days')]);

    // then
    expect(period).toEqual({value: 30, unit: 'days'});
  });

  it('should return null when there is no rolling end-date filter', () => {
    expect(getRollingPeriod([{type: 'runningInstancesOnly', data: null}])).toBeNull();
    expect(getRollingPeriod([])).toBeNull();
    expect(getRollingPeriod(undefined)).toBeNull();
  });

  it('should ignore a fixed end-date filter', () => {
    // given
    const filter = [{type: 'instanceEndDate', data: {type: 'fixed', start: 'x', end: 'y'}}];

    // then
    expect(getRollingPeriod(filter)).toBeNull();
  });
});

describe('buildBaselineFilter', () => {
  beforeAll(() => {
    jest.useFakeTimers().setSystemTime(new Date('2026-06-15T12:00:00.000Z'));
  });

  afterAll(() => {
    jest.useRealTimers();
  });

  it('should return null when there is no filter', () => {
    expect(buildBaselineFilter(undefined)).toBeNull();
    expect(buildBaselineFilter(null)).toBeNull();
  });

  it('should return null when there is no rolling window to mirror', () => {
    // given – a real filter, but no rolling end-date filter to base a comparison on
    const filter = [{type: 'runningInstancesOnly', data: null}];

    // then – null so the caller skips the fetch instead of comparing against itself
    expect(buildBaselineFilter(filter)).toBeNull();
  });

  it('should build the immediately preceding equal-length window for a day preset', () => {
    // when – "last 30 days" on 2026-06-15
    const baseline = buildBaselineFilter([rollingFilter(30, 'days')])![0]!;

    // then – a fixed window spanning the prior 30 days (60..30 days ago)
    expect(baseline.data.type).toBe('fixed');
    expect(baseline.data.includeUndefined).toBe(false);
    expect(baseline.data.excludeUndefined).toBe(false);
    expect(asUTCDate(baseline.data.end)).toEqual([2026, 4, 16]); // 2026-05-16
    expect(asUTCDate(baseline.data.start)).toEqual([2026, 3, 16]); // 2026-04-16
  });

  it('should compute month presets calendar-aware, not as fixed milliseconds', () => {
    // when – "last 3 months" on 2026-06-15
    const baseline = buildBaselineFilter([rollingFilter(3, 'months')])![0]!;

    // then – the prior 3 calendar months (2025-12-15 .. 2026-03-15), landing on the
    // same day-of-month rather than drifting by average-month millisecond lengths
    expect(asUTCDate(baseline.data.end)).toEqual([2026, 2, 15]); // 2026-03-15
    expect(asUTCDate(baseline.data.start)).toEqual([2025, 11, 15]); // 2025-12-15
  });

  it('should preserve non-date filters and replace the rolling end-date filter', () => {
    // given
    const filter = [{type: 'runningInstancesOnly', data: null}, rollingFilter(7, 'days')];

    // when
    const result = buildBaselineFilter(filter)!;

    // then
    expect(result).toHaveLength(2);
    expect(result[0]).toEqual({type: 'runningInstancesOnly', data: null});
    expect(result[1]?.data.type).toBe('fixed');
  });
});

describe('classifyDelta', () => {
  it('should classify an unchanged value as neutral', () => {
    expect(classifyDelta(100, 100, 'up')).toEqual({delta: 0, direction: 'neutral'});
  });

  it('should classify an increase as good when up is the good direction', () => {
    expect(classifyDelta(120, 100, 'up')).toEqual({delta: 20, direction: 'good'});
  });

  it('should classify a decrease as bad when up is the good direction', () => {
    expect(classifyDelta(80, 100, 'up')).toEqual({delta: -20, direction: 'bad'});
  });

  it('should classify a decrease as good when down is the good direction', () => {
    expect(classifyDelta(80, 100, 'down')).toEqual({delta: -20, direction: 'good'});
  });

  it('should classify an increase as bad when down is the good direction', () => {
    expect(classifyDelta(120, 100, 'down')).toEqual({delta: 20, direction: 'bad'});
  });
});

describe('formatDelta', () => {
  it('should format a plain number with a sign', () => {
    expect(formatDelta(130, '')).toBe('+130');
    expect(formatDelta(-20, '')).toBe('-20');
    expect(formatDelta(0, '')).toBe('+0');
  });

  it('should format durations below a second in milliseconds', () => {
    expect(formatDelta(-50, 'ms')).toBe('-50ms');
  });

  it('should format durations of a second or more in seconds', () => {
    expect(formatDelta(-1500, 'ms')).toBe('-1.5s');
  });

  it('should format percentages with one decimal below 1% and none otherwise', () => {
    expect(formatDelta(-0.1, '%')).toBe('-0.1%');
    expect(formatDelta(2, '%')).toBe('+2%');
  });
});

describe('propertyToValueUnit', () => {
  it('should map duration to ms and percentage to %', () => {
    expect(propertyToValueUnit('duration')).toBe('ms');
    expect(propertyToValueUnit('percentage')).toBe('%');
  });

  it('should map anything else to no unit', () => {
    expect(propertyToValueUnit('frequency')).toBe('');
    expect(propertyToValueUnit(undefined)).toBe('');
  });
});

describe('comparisonLabelKey', () => {
  it.each([
    [7, 'days', 'wow'],
    [30, 'days', 'mom'],
    [3, 'months', 'qoq'],
    [6, 'months', 'hoh'],
    [12, 'months', 'yoy'],
  ])('should map the %s-%s preset to "%s"', (value, unit, expected) => {
    expect(comparisonLabelKey({value: value as number, unit: unit as string})).toBe(expected);
  });

  it('should return null for an unknown preset or missing period', () => {
    expect(comparisonLabelKey({value: 14, unit: 'days'})).toBeNull();
    expect(comparisonLabelKey(null)).toBeNull();
  });
});

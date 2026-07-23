/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Replaces the "Cohort survival" ShareTrendTile in KpiTab, which never rendered: it asked the
 * registry for an `instance_cohorts` measure/counter named like "started"/"total"/"1h"/"1d" (see
 * ShareTrendTile's totalMeasureCandidates/bands), but `instance_cohorts` only declares a
 * `duration_ms` scalar+histogram measure -- no counters and no bare `cnt` for a share denominator,
 * so ShareTrendTile's `ready` gate was permanently false. There is also no tools/series primitive
 * for "share of mass at/below a threshold" (tools/series's quantile mode only answers the inverse:
 * value-at-quantile) -- so this tile calls the dedicated typed `POST /api/tools/cohort-share`
 * endpoint (see lib/gaugeApi.ts / CohortShareService) instead of tools/series.
 */
import { useEffect, useState } from "react";
import {
  CartesianGrid,
  ComposedChart,
  Legend,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { chartColor } from "../../../lib/chartColors";
import { formatPercent, formatWindowForSpan } from "../../../lib/format";
import { gaugeApi } from "../../../lib/gaugeApi";
import { ChartCard } from "../../common/ChartCard";
import { EmptyTile, LoadingTile } from "../../common/EmptyTile";

const WITHIN_1H_MS = 60 * 60 * 1000;
const WITHIN_1D_MS = 24 * WITHIN_1H_MS;

interface Row {
  label: string;
  within1h: number | null;
  within1d: number | null;
}

export function CohortWithinShareTile({
  from,
  to,
  grainMinutes,
}: {
  from: number;
  to: number;
  grainMinutes: number;
}) {
  const [rows, setRows] = useState<Row[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    gaugeApi
      .cohortShare({
        entity: "instance_cohorts",
        thresholdsMs: [WITHIN_1H_MS, WITHIN_1D_MS],
        filters: {},
        from,
        to,
        grainMinutes,
      })
      .then((result) => {
        if (cancelled) {
          return;
        }
        if (!result.ok) {
          setError(result.message);
          setLoading(false);
          return;
        }
        const [within1h, within1d] = result.data.series;
        if (!within1h || !within1d) {
          setError("Unexpected series shape from cohort-share");
          setLoading(false);
          return;
        }
        const span = to - from;
        const within1dByT = new Map(within1d.points.map((p) => [p.t, p.share]));
        setRows(
          within1h.points.map((p) => ({
            label: formatWindowForSpan(p.t, span),
            within1h: p.share,
            within1d: within1dByT.get(p.t) ?? null,
          })),
        );
        setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [from, to, grainMinutes]);

  return (
    <ChartCard
      title="Cohort survival"
      description="Share of each start-hour cohort completed within 1h / 1d"
    >
      {loading ? (
        <LoadingTile />
      ) : error || !rows ? (
        <EmptyTile
          heading="Not available yet"
          description={error ?? "instance_cohorts_hist isn't in this warehouse yet."}
        />
      ) : rows.length === 0 ? (
        <EmptyTile heading="No data in range" />
      ) : (
        <ResponsiveContainer width="100%" height="100%">
          <ComposedChart data={rows} margin={{ top: 8, right: 16, bottom: 4, left: 8 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border, #e5e7eb)" />
            <XAxis dataKey="label" tick={{ fontSize: 12 }} />
            <YAxis
              domain={[0, 1]}
              tickFormatter={(v: number) => formatPercent(v)}
              width={48}
              tick={{ fontSize: 12 }}
            />
            <Tooltip formatter={(value, name) => [formatPercent(Number(value)), String(name)]} />
            <Legend />
            {/* Two cumulative-share curves, not stacked -- "≤ 1d" already includes every "≤ 1h"
             * case (bin_hi <= 1d is a superset of bin_hi <= 1h), so stacking them would double-count
             * the same completions. */}
            <Line type="monotone" dataKey="within1h" name="≤ 1h" stroke={chartColor(2)} strokeWidth={2} dot={false} />
            <Line type="monotone" dataKey="within1d" name="≤ 1d" stroke={chartColor(0)} strokeWidth={2} dot={false} />
          </ComposedChart>
        </ResponsiveContainer>
      )}
    </ChartCard>
  );
}

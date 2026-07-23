/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useEffect, useState } from "react";
import {
  Bar,
  CartesianGrid,
  ComposedChart,
  Legend,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { api } from "../../../lib/api";
import { chartColor } from "../../../lib/chartColors";
import { formatPercent, formatWindowForSpan } from "../../../lib/format";
import { useAppData } from "../../../lib/appData";
import { findMeasure } from "../../../lib/registryHelpers";
import { ChartCard } from "../../common/ChartCard";
import { EmptyTile, LoadingTile } from "../../common/EmptyTile";

export interface ShareBand {
  /** Substring candidates matched (case-insensitively) against the entity's registry measures --
   * see lib/registryHelpers.ts for why this is a guess-and-fall-back, not an exact name. */
  candidates: string[];
  label: string;
  colorIndex: number;
}

/**
 * Renders one or more "share of total" bands over time -- built from the series tool (a numerator
 * measure divided by a total measure per window), since decompose/series don't have a dedicated
 * cohort-share primitive. Used for cohort survival (share completed within 1h/1d, stacked) and the
 * object survival curve (share closed by age, as a rising line) -- same shape, different mode.
 *
 * Every measure name is resolved against the live registry (not hardcoded), so if the entity or
 * its expected measures aren't there yet, the tile renders its empty state rather than a chart
 * built from a guess that happened to be wrong.
 */
export function ShareTrendTile({
  title,
  description,
  entity,
  totalMeasureCandidates,
  bands,
  from,
  to,
  grainMinutes,
  mode = "stacked-bar",
  filters,
}: {
  title: string;
  description?: string;
  entity: string;
  totalMeasureCandidates: string[];
  bands: ShareBand[];
  from: number;
  to: number;
  grainMinutes: number;
  mode?: "stacked-bar" | "line";
  filters?: Record<string, string | number | boolean>;
}) {
  const { entities } = useAppData();
  const entityDescriptor = entities.find((e) => e.name === entity);
  const totalMeasure = findMeasure(entityDescriptor, totalMeasureCandidates);
  const resolvedBands = bands
    .map((b) => ({ ...b, measure: findMeasure(entityDescriptor, b.candidates) }))
    .filter((b): b is ShareBand & { measure: string } => b.measure != null);

  const [rows, setRows] = useState<Record<string, number | string>[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const ready = totalMeasure != null && resolvedBands.length > 0;

  useEffect(() => {
    if (!ready || totalMeasure == null) {
      setLoading(false);
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(null);

    Promise.all([
      api.tools.series({ entity, measure: totalMeasure, quantile: null, filters: filters ?? {}, from, to, grainMinutes }),
      ...resolvedBands.map((b) =>
        api.tools.series({ entity, measure: b.measure, quantile: null, filters: filters ?? {}, from, to, grainMinutes }),
      ),
    ]).then((results) => {
      if (cancelled) {
        return;
      }
      const failed = results.find((r) => !r.ok);
      if (failed && !failed.ok) {
        setError(failed.message);
        setLoading(false);
        return;
      }
      const [totalResult, ...bandResults] = results;
      const span = to - from;
      const byT = new Map<string, Record<string, number | string>>();
      if (totalResult.ok) {
        for (const p of totalResult.data.points) {
          byT.set(p.t, { t: p.t, label: formatWindowForSpan(p.t, span), total: p.value });
        }
      }
      bandResults.forEach((result, i) => {
        if (!result.ok) {
          return;
        }
        for (const p of result.data.points) {
          const row = byT.get(p.t);
          if (row) {
            const total = typeof row.total === "number" ? row.total : 0;
            row[`band${i}`] = total > 0 ? p.value / total : 0;
          }
        }
      });
      setRows(
        [...byT.values()].sort(
          (a, b) => new Date(a.t as string).getTime() - new Date(b.t as string).getTime(),
        ),
      );
      setLoading(false);
    });

    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [entity, totalMeasure, ready, from, to, grainMinutes]);

  return (
    <ChartCard title={title} description={description}>
      {!ready ? (
        <EmptyTile
          heading="Not available yet"
          description={`${entity} isn't in the registry with the expected measures yet.`}
        />
      ) : loading ? (
        <LoadingTile />
      ) : error || !rows ? (
        <EmptyTile heading="Not available yet" description={error ?? "No data."} />
      ) : rows.length === 0 ? (
        <EmptyTile heading="No data in range" />
      ) : (
        <ResponsiveContainer width="100%" height="100%">
          <ComposedChart data={rows} margin={{ top: 8, right: 16, bottom: 4, left: 8 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border, #e5e7eb)" />
            <XAxis dataKey="label" tick={{ fontSize: 12 }} />
            <YAxis
              domain={[0, mode === "stacked-bar" ? 1 : "auto"]}
              tickFormatter={(v: number) => formatPercent(v)}
              width={48}
              tick={{ fontSize: 12 }}
            />
            <Tooltip formatter={(value, name) => [formatPercent(Number(value)), String(name)]} />
            <Legend />
            {resolvedBands.map((b, i) =>
              mode === "stacked-bar" ? (
                <Bar
                  key={i}
                  dataKey={`band${i}`}
                  name={b.label}
                  stackId="shares"
                  fill={chartColor(b.colorIndex)}
                />
              ) : (
                <Line
                  key={i}
                  type="monotone"
                  dataKey={`band${i}`}
                  name={b.label}
                  stroke={chartColor(b.colorIndex)}
                  strokeWidth={2}
                  dot={false}
                />
              ),
            )}
          </ComposedChart>
        </ResponsiveContainer>
      )}
    </ChartCard>
  );
}

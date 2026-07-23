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
import { api, type Filters } from "../../../lib/api";
import { chartColor } from "../../../lib/chartColors";
import { formatWindow } from "../../../lib/format";
import { ChartCard } from "../../common/ChartCard";
import { EmptyTile, LoadingTile } from "../../common/EmptyTile";
import { ExplainLink } from "../../common/ExplainLink";

export interface SeriesSpec {
  entity: string;
  measure?: string | null;
  quantile?: number | null;
  filters?: Filters;
  label: string;
  colorIndex: number;
}

interface Row {
  /** ISO-8601 window start, straight off the backend's SeriesPoint. */
  t: string;
  label: string;
  [seriesKey: string]: number | string;
}

/**
 * The tools/series-backed tile: fetches one or more named series over the same window/grain and
 * overlays them as bars or lines. Used for every "X per slot" / "X trend" tile on the dashboards
 * (started/completed counts, duration percentiles, ...). The ⌕ action pre-fills Explain from the
 * first series (the tile's primary metric).
 */
export function SeriesTile({
  title,
  description,
  series,
  from,
  to,
  grainMinutes,
  chartType = "bar",
  valueFormatter,
}: {
  title: string;
  description?: string;
  series: SeriesSpec[];
  from: number;
  to: number;
  grainMinutes: number;
  chartType?: "bar" | "line";
  valueFormatter?: (v: number) => string;
}) {
  const [rows, setRows] = useState<Row[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    setRows(null);

    Promise.all(
      series.map((s) =>
        api.tools.series({
          entity: s.entity,
          measure: s.measure ?? null,
          quantile: s.quantile ?? null,
          filters: s.filters ?? {},
          from,
          to,
          grainMinutes,
        }),
      ),
    ).then((results) => {
      if (cancelled) {
        return;
      }
      const failed = results.find((r) => !r.ok);
      if (failed && !failed.ok) {
        setError(failed.message);
        setLoading(false);
        return;
      }
      const byT = new Map<string, Row>();
      results.forEach((result, i) => {
        if (!result.ok) {
          return;
        }
        for (const p of result.data.points) {
          let row = byT.get(p.t);
          if (!row) {
            row = { t: p.t, label: formatWindow(p.t) };
            byT.set(p.t, row);
          }
          row[`s${i}`] = p.value;
        }
      });
      setRows(
        [...byT.values()].sort((a, b) => new Date(a.t).getTime() - new Date(b.t).getTime()),
      );
      setLoading(false);
    });

    return () => {
      cancelled = true;
    };
  }, [series, from, to, grainMinutes]);

  const primary = series[0];
  const action = primary ? (
    <ExplainLink
      prefill={{
        entity: primary.entity,
        measure: primary.measure,
        quantile: primary.quantile,
        filters: primary.filters,
        from,
        to,
      }}
    />
  ) : null;

  return (
    <ChartCard title={title} description={description} action={action}>
      {loading ? (
        <LoadingTile />
      ) : error || !rows ? (
        <EmptyTile
          heading="Not available yet"
          description={error ?? "This series isn't in the registry yet."}
        />
      ) : rows.length === 0 ? (
        <EmptyTile heading="No data in range" description="No points returned for this window." />
      ) : (
        <ResponsiveContainer width="100%" height="100%">
          <ComposedChart data={rows} margin={{ top: 8, right: 16, bottom: 4, left: 8 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border, #e5e7eb)" />
            <XAxis dataKey="label" tick={{ fontSize: 12 }} />
            <YAxis
              allowDecimals={false}
              width={48}
              tick={{ fontSize: 12 }}
              tickFormatter={valueFormatter}
            />
            <Tooltip
              formatter={(value, name) => [
                valueFormatter ? valueFormatter(Number(value)) : String(value),
                String(name),
              ]}
              labelFormatter={(label) => `Window ${String(label)}`}
            />
            {series.length > 1 ? <Legend /> : null}
            {series.map((s, i) =>
              chartType === "bar" ? (
                <Bar
                  key={i}
                  dataKey={`s${i}`}
                  name={s.label}
                  fill={chartColor(s.colorIndex)}
                  radius={[4, 4, 0, 0]}
                />
              ) : (
                <Line
                  key={i}
                  type="monotone"
                  dataKey={`s${i}`}
                  name={s.label}
                  stroke={chartColor(s.colorIndex)}
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

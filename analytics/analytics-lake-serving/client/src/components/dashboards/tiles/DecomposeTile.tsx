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
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { api, type DecomposeRow } from "../../../lib/api";
import { chartColor } from "../../../lib/chartColors";
import { formatPercent } from "../../../lib/format";
import { ChartCard } from "../../common/ChartCard";
import { EmptyTile, LoadingTile } from "../../common/EmptyTile";
import { ExplainLink } from "../../common/ExplainLink";

/**
 * A ranked breakdown of one entity's rows by dimension value, via tools/decompose -- the only tool
 * in the contract that returns a per-dimension-value breakdown (the series tool only returns an
 * overall time series). Used for every "by <dim>" bar tile on the dashboards (throughput by
 * process, per-element p95, per-variant p95, branch shares, null-rate by variable).
 *
 * Decompose is a window-vs-baseline comparison tool; when the tile only wants a current-value
 * ranking (not a change explanation), the baseline defaults to the equal-length period immediately
 * before the window, purely so the request is well-formed -- the tile itself renders `current`,
 * not `delta`/`contributionShare` (those surface in the tooltip as secondary context).
 */
export function DecomposeTile({
  title,
  description,
  entity,
  measure = null,
  quantile = null,
  dim,
  from,
  to,
  topN = 10,
  valueFormatter,
}: {
  title: string;
  description?: string;
  entity: string;
  measure?: string | null;
  quantile?: number | null;
  dim: string;
  from: number;
  to: number;
  topN?: number;
  valueFormatter?: (v: number) => string;
}) {
  const [rows, setRows] = useState<DecomposeRow[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    setRows(null);

    const span = to - from;
    api.tools
      .decompose({
        entity,
        measure,
        quantile,
        window: { from, to },
        baseline: { from: from - span, to: from },
        dim,
      })
      .then((result) => {
        if (cancelled) {
          return;
        }
        if (!result.ok) {
          setError(result.message);
        } else {
          setRows(
            [...result.data.rows].sort((a, b) => b.current - a.current).slice(0, topN),
          );
        }
        setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [entity, measure, quantile, dim, from, to, topN]);

  const action = (
    <ExplainLink prefill={{ entity, measure, quantile, from, to }} />
  );

  return (
    <ChartCard title={title} description={description} action={action}>
      {loading ? (
        <LoadingTile />
      ) : error || !rows ? (
        <EmptyTile
          heading="Not available yet"
          description={error ?? `No breakdown by ${dim} yet.`}
        />
      ) : rows.length === 0 ? (
        <EmptyTile heading="No rows in range" />
      ) : (
        <ResponsiveContainer width="100%" height="100%">
          <BarChart
            data={rows}
            layout="vertical"
            margin={{ top: 8, right: 24, bottom: 4, left: 8 }}
          >
            <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border, #e5e7eb)" />
            <XAxis
              type="number"
              tick={{ fontSize: 12 }}
              tickFormatter={valueFormatter}
              allowDecimals={false}
            />
            <YAxis
              type="category"
              dataKey="value"
              width={110}
              tick={{ fontSize: 11 }}
              interval={0}
            />
            <Tooltip
              formatter={(value, name, item) => {
                const numeric = Number(value);
                if (name === "current") {
                  const share = (item?.payload as DecomposeRow | undefined)?.contributionShare;
                  return [
                    `${valueFormatter ? valueFormatter(numeric) : numeric}${
                      share != null ? ` (${formatPercent(share)} of change)` : ""
                    }`,
                    "Value",
                  ];
                }
                return [String(value), String(name)];
              }}
            />
            <Bar dataKey="current" fill={chartColor(0)} radius={[0, 4, 4, 0]} />
          </BarChart>
        </ResponsiveContainer>
      )}
    </ChartCard>
  );
}

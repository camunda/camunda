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
import { Button, Input } from "@camunda/design-system";
import { api, type DecomposeRequest, type DecomposeResponse } from "../../../lib/api";
import { chartColor } from "../../../lib/chartColors";
import { formatPercent } from "../../../lib/format";
import { EmptyTile, LoadingTile } from "../../common/EmptyTile";

function defaultRequest(): DecomposeRequest {
  const to = Date.now();
  const from = to - 24 * 3600_000;
  return {
    entity: "",
    measure: null,
    quantile: null,
    window: { from, to },
    baseline: { from: from - (to - from), to: from },
    dim: "",
  };
}

/** Manual "decompose" tool panel: window-vs-baseline contribution breakdown by dimension, rendered
 * as contribution-share bars (per the spec: "decompose → contribution bars"). `autoRun` fetches
 * immediately once a `prefill` arrives -- see {@link SeriesPanel}'s doc comment for why. */
export function DecomposePanel({
  prefill,
  autoRun,
}: {
  prefill?: Partial<DecomposeRequest>;
  autoRun?: boolean;
}) {
  const [req, setReq] = useState<DecomposeRequest>({ ...defaultRequest(), ...prefill });
  const [result, setResult] = useState<DecomposeResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const runRequest = (r: DecomposeRequest) => {
    setLoading(true);
    setError(null);
    api.tools.decompose(r).then((res) => {
      if (!res.ok) {
        setError(res.message);
        setResult(null);
      } else {
        setResult(res.data);
      }
      setLoading(false);
    });
  };

  useEffect(() => {
    const merged = { ...req, ...prefill };
    setReq(merged);
    if (autoRun && prefill && merged.entity && merged.dim) {
      runRequest(merged);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [JSON.stringify(prefill)]);

  const run = () => runRequest(req);

  const data = (result?.rows ?? []).map((r) => ({ ...r, sharePct: r.contributionShare * 100 }));

  return (
    <div className="flex flex-col gap-3">
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
        <Input
          placeholder="entity"
          value={req.entity}
          onChange={(e) => setReq((r) => ({ ...r, entity: e.target.value }))}
        />
        <Input
          placeholder="measure"
          value={req.measure ?? ""}
          onChange={(e) => setReq((r) => ({ ...r, measure: e.target.value || null }))}
        />
        <Input
          placeholder="dim"
          value={req.dim}
          onChange={(e) => setReq((r) => ({ ...r, dim: e.target.value }))}
        />
      </div>
      <div>
        <Button size="sm" onClick={run} disabled={loading || !req.entity || !req.dim}>
          {loading ? "Running…" : "Run"}
        </Button>
      </div>
      {loading ? <LoadingTile /> : null}
      {error ? <EmptyTile heading="Not available yet" description={error} /> : null}
      {result && !error ? (
        result.rows.length === 0 ? (
          <EmptyTile heading="No rows in range" />
        ) : (
          <div className="h-72 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={data} layout="vertical" margin={{ top: 8, right: 24, bottom: 4, left: 8 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border, #e5e7eb)" />
                <XAxis
                  type="number"
                  tickFormatter={(v: number) => `${v}%`}
                  tick={{ fontSize: 12 }}
                />
                <YAxis type="category" dataKey="value" width={120} tick={{ fontSize: 11 }} interval={0} />
                <Tooltip formatter={(v) => formatPercent(Number(v) / 100)} />
                <Bar dataKey="sharePct" fill={chartColor(0)} radius={[0, 4, 4, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        )
      ) : null}
    </div>
  );
}

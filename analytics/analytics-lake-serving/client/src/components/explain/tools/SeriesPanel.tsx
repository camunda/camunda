/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useEffect, useState } from "react";
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { Button, Input } from "@camunda/design-system";
import { api, type SeriesRequest, type SeriesResponse } from "../../../lib/api";
import { chartColor } from "../../../lib/chartColors";
import { formatWindowForSpan } from "../../../lib/format";
import { EmptyTile, LoadingTile } from "../../common/EmptyTile";

function defaultRequest(): SeriesRequest {
  const to = Date.now();
  return { entity: "", measure: null, quantile: null, filters: {}, from: to - 24 * 3600_000, to, grainMinutes: 60 };
}

/** Manual "series" tool panel: typed form mirroring {@link SeriesRequest} exactly, rendered as a
 * line chart -- the tool-panel counterpart to the auto-generated series-backed dashboard tiles. */
export function SeriesPanel({ prefill }: { prefill?: Partial<SeriesRequest> }) {
  const [req, setReq] = useState<SeriesRequest>({ ...defaultRequest(), ...prefill });
  const [result, setResult] = useState<SeriesResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setReq((r) => ({ ...r, ...prefill }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [JSON.stringify(prefill)]);

  const run = () => {
    setLoading(true);
    setError(null);
    api.tools.series(req).then((res) => {
      if (!res.ok) {
        setError(res.message);
        setResult(null);
      } else {
        setResult(res.data);
      }
      setLoading(false);
    });
  };

  const span = req.to - req.from;
  const data = result?.points.map((p) => ({ label: formatWindowForSpan(p.t, span), value: p.value })) ?? [];

  return (
    <div className="flex flex-col gap-3">
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
        <Input
          placeholder="entity"
          value={req.entity}
          onChange={(e) => setReq((r) => ({ ...r, entity: e.target.value }))}
        />
        <Input
          placeholder="measure"
          value={req.measure ?? ""}
          onChange={(e) => setReq((r) => ({ ...r, measure: e.target.value || null, quantile: null }))}
        />
        <Input
          type="number"
          placeholder="quantile"
          value={req.quantile ?? ""}
          onChange={(e) =>
            setReq((r) => ({
              ...r,
              quantile: e.target.value === "" ? null : Number(e.target.value),
              measure: e.target.value === "" ? r.measure : null,
            }))
          }
        />
        <Input
          type="number"
          placeholder="grain (min)"
          value={req.grainMinutes}
          onChange={(e) => setReq((r) => ({ ...r, grainMinutes: Number(e.target.value) }))}
        />
      </div>
      <div>
        <Button size="sm" onClick={run} disabled={loading || !req.entity}>
          {loading ? "Running…" : "Run"}
        </Button>
      </div>
      {loading ? <LoadingTile /> : null}
      {error ? <EmptyTile heading="Not available yet" description={error} /> : null}
      {result && !error ? (
        result.points.length === 0 ? (
          <EmptyTile heading="No data in range" />
        ) : (
          <div className="h-72 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={data} margin={{ top: 8, right: 16, bottom: 4, left: 8 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border, #e5e7eb)" />
                <XAxis dataKey="label" tick={{ fontSize: 12 }} />
                <YAxis width={48} tick={{ fontSize: 12 }} />
                <Tooltip />
                <Line type="monotone" dataKey="value" stroke={chartColor(0)} strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        )
      ) : null}
    </div>
  );
}

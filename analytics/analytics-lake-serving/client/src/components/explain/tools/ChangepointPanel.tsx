/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useEffect, useState } from "react";
import { Badge, Button, Input } from "@camunda/design-system";
import { api, type ChangepointResponse, type SeriesRequest } from "../../../lib/api";
import { formatCount, formatDateTime, formatPercent } from "../../../lib/format";
import { EmptyTile, LoadingTile } from "../../common/EmptyTile";

function defaultRequest(): SeriesRequest {
  const to = Date.now();
  return { entity: "", measure: null, quantile: null, filters: {}, from: to - 24 * 3600_000, to, grainMinutes: 60 };
}

/** Manual "changepoint" tool panel -- same request shape as series (per the contract), rendered as
 * a plain-language summary of the detected shape/confidence/before-after. */
export function ChangepointPanel({ prefill }: { prefill?: Partial<SeriesRequest> }) {
  const [req, setReq] = useState<SeriesRequest>({ ...defaultRequest(), ...prefill });
  const [result, setResult] = useState<ChangepointResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setReq((r) => ({ ...r, ...prefill }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [JSON.stringify(prefill)]);

  const run = () => {
    setLoading(true);
    setError(null);
    api.tools.changepoint(req).then((res) => {
      if (!res.ok) {
        setError(res.message);
        setResult(null);
      } else {
        setResult(res.data);
      }
      setLoading(false);
    });
  };

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
          onChange={(e) => setReq((r) => ({ ...r, measure: e.target.value || null }))}
        />
        <Input
          type="number"
          placeholder="quantile"
          value={req.quantile ?? ""}
          onChange={(e) => setReq((r) => ({ ...r, quantile: e.target.value === "" ? null : Number(e.target.value) }))}
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
        result.shape === "NONE" ? (
          <EmptyTile heading="No changepoint detected" />
        ) : (
          <div className="flex flex-col gap-2 rounded border border-border p-4">
            <div className="flex items-center gap-2">
              <Badge variant="warning">{result.shape}</Badge>
              <span className="text-sm text-neutral-foreground-muted">
                at {formatDateTime(result.at)} · confidence {formatPercent(result.confidence)}
              </span>
            </div>
            <span className="text-sm">
              {formatCount(result.before)} → {formatCount(result.after)}
            </span>
          </div>
        )
      ) : null}
    </div>
  );
}

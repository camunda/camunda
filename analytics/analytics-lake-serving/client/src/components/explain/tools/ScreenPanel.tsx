/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useEffect, useState } from "react";
import { Button, Input, Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@camunda/design-system";
import { api, type ScreenRequest, type ScreenResponse } from "../../../lib/api";
import { formatDateTime } from "../../../lib/format";
import { EmptyTile, LoadingTile } from "../../common/EmptyTile";

function defaultRequest(): ScreenRequest {
  const to = Date.now();
  const from = to - 24 * 3600_000;
  return {
    targetSeries: { entity: "", measure: null, quantile: null, filters: {}, from, to, grainMinutes: 60 },
    window: { from, to },
    candidates: "auto",
  };
}

/** Manual "screen" tool panel: scans candidate series that moved in lockstep with the target
 * series (candidates is always "auto" per the contract -- no other mode is documented). */
export function ScreenPanel({ prefill }: { prefill?: Partial<ScreenRequest> }) {
  const [req, setReq] = useState<ScreenRequest>({ ...defaultRequest(), ...prefill });
  const [result, setResult] = useState<ScreenResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setReq((r) => ({ ...r, ...prefill }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [JSON.stringify(prefill)]);

  const run = () => {
    setLoading(true);
    setError(null);
    api.tools.screen(req).then((res) => {
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
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
        <Input
          placeholder="target entity"
          value={req.targetSeries.entity}
          onChange={(e) =>
            setReq((r) => ({ ...r, targetSeries: { ...r.targetSeries, entity: e.target.value } }))
          }
        />
        <Input
          placeholder="target measure"
          value={req.targetSeries.measure ?? ""}
          onChange={(e) =>
            setReq((r) => ({ ...r, targetSeries: { ...r.targetSeries, measure: e.target.value || null } }))
          }
        />
      </div>
      <div>
        <Button size="sm" onClick={run} disabled={loading || !req.targetSeries.entity}>
          {loading ? "Screening…" : "Run"}
        </Button>
      </div>
      {loading ? <LoadingTile /> : null}
      {error ? <EmptyTile heading="Not available yet" description={error} /> : null}
      {result && !error ? (
        result.rows.length === 0 ? (
          <EmptyTile heading="No correlated candidates found" />
        ) : (
          <Table size="sm">
            <TableHeader>
              <TableRow>
                <TableHead>Series</TableHead>
                <TableHead className="text-right">Shift (slots)</TableHead>
                <TableHead className="text-right">Correlation</TableHead>
                <TableHead className="text-right">Moved at</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {result.rows.map((r, i) => (
                <TableRow key={i}>
                  <TableCell>{r.series}</TableCell>
                  <TableCell className="text-right tabular-nums">{r.shiftSlots}</TableCell>
                  <TableCell className="text-right tabular-nums">{r.correlation.toFixed(2)}</TableCell>
                  <TableCell className="text-right tabular-nums">{formatDateTime(r.movedAt)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )
      ) : null}
    </div>
  );
}

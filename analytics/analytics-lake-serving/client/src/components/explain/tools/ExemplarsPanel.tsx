/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useEffect, useState } from "react";
import { Button, Input, Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@camunda/design-system";
import { api, type ExemplarsRequest, type ExemplarsResponse } from "../../../lib/api";
import { formatDateTime, formatDuration } from "../../../lib/format";
import { EmptyTile, LoadingTile } from "../../common/EmptyTile";

function defaultRequest(): ExemplarsRequest {
  return { entity: "", cohort: { type: "THRESHOLD", measure: "", op: ">", value: 0 }, k: 10 };
}

/**
 * Manual "exemplars" tool panel: k representative instances of a cohort. The spec calls for "row
 * list linking to Objects/journey where applicable" -- ExemplarRow only carries
 * instanceKey/durationMs/startedAt/variantHash, with no objectId the contract guarantees links
 * back to any object, so there is nothing to link to here; rows render as a plain table rather
 * than a fabricated link.
 */
export function ExemplarsPanel({ prefill }: { prefill?: Partial<ExemplarsRequest> }) {
  const [req, setReq] = useState<ExemplarsRequest>({ ...defaultRequest(), ...prefill });
  const [result, setResult] = useState<ExemplarsResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setReq((r) => ({ ...r, ...prefill }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [JSON.stringify(prefill)]);

  const run = () => {
    setLoading(true);
    setError(null);
    api.tools.exemplars(req).then((res) => {
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
          placeholder="entity"
          value={req.entity}
          onChange={(e) => setReq((r) => ({ ...r, entity: e.target.value }))}
        />
        <Input
          type="number"
          placeholder="k"
          value={req.k}
          onChange={(e) => setReq((r) => ({ ...r, k: Number(e.target.value) }))}
        />
      </div>
      <div>
        <Button size="sm" onClick={run} disabled={loading || !req.entity}>
          {loading ? "Sampling…" : "Run"}
        </Button>
      </div>
      {loading ? <LoadingTile /> : null}
      {error ? <EmptyTile heading="Not available yet" description={error} /> : null}
      {result && !error ? (
        result.rows.length === 0 ? (
          <EmptyTile heading="No exemplars for this cohort" />
        ) : (
          <Table size="sm">
            <TableHeader>
              <TableRow>
                <TableHead>Instance</TableHead>
                <TableHead>Started</TableHead>
                <TableHead className="text-right">Duration</TableHead>
                <TableHead>Path</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {result.rows.map((r) => (
                <TableRow key={r.instanceKey}>
                  <TableCell className="font-mono text-xs">{r.instanceKey}</TableCell>
                  <TableCell>{formatDateTime(r.startedAt)}</TableCell>
                  <TableCell className="text-right tabular-nums">{formatDuration(r.durationMs)}</TableCell>
                  <TableCell className="font-mono text-xs">{r.variantHash}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )
      ) : null}
    </div>
  );
}

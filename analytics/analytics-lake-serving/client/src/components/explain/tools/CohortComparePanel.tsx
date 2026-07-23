/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useEffect, useState } from "react";
import { Button, Input, Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@camunda/design-system";
import { api, type CohortCompareRequest, type CohortCompareResponse } from "../../../lib/api";
import { EmptyTile, LoadingTile } from "../../common/EmptyTile";
import { LiftTable } from "../FindingCard";

function defaultRequest(): CohortCompareRequest {
  const to = Date.now();
  const from = to - 24 * 3600_000;
  return {
    entity: "",
    cohort: { type: "THRESHOLD", measure: "", op: ">", value: 0 },
    filters: {},
    from,
    to,
    attributes: "auto",
    supportFloor: 20,
  };
}

/** Manual "cohort-compare" tool panel: splits instances into a slow/fast cohort (threshold on a
 * measure, or a before/after window split) and ranks attributes by lift. */
export function CohortComparePanel({ prefill }: { prefill?: Partial<CohortCompareRequest> }) {
  const [req, setReq] = useState<CohortCompareRequest>({ ...defaultRequest(), ...prefill });
  const [result, setResult] = useState<CohortCompareResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setReq((r) => ({ ...r, ...prefill }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [JSON.stringify(prefill)]);

  const run = () => {
    setLoading(true);
    setError(null);
    api.tools.cohortCompare(req).then((res) => {
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
        <Select
          value={req.cohort.type}
          onValueChange={(v) =>
            setReq((r) => ({
              ...r,
              cohort:
                v === "THRESHOLD"
                  ? { type: "THRESHOLD", measure: "", op: ">", value: 0 }
                  : { type: "WINDOW_SPLIT", at: Date.now() },
            }))
          }
        >
          <SelectTrigger size="sm">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="THRESHOLD">Threshold</SelectItem>
            <SelectItem value="WINDOW_SPLIT">Window split</SelectItem>
          </SelectContent>
        </Select>
        {req.cohort.type === "THRESHOLD" ? (
          <>
            <Input
              placeholder="measure"
              value={req.cohort.measure}
              onChange={(e) =>
                setReq((r) =>
                  r.cohort.type === "THRESHOLD" ? { ...r, cohort: { ...r.cohort, measure: e.target.value } } : r,
                )
              }
            />
            <Input
              type="number"
              placeholder="value"
              value={req.cohort.value}
              onChange={(e) =>
                setReq((r) =>
                  r.cohort.type === "THRESHOLD"
                    ? { ...r, cohort: { ...r.cohort, value: Number(e.target.value) } }
                    : r,
                )
              }
            />
          </>
        ) : (
          <Input
            type="datetime-local"
            onChange={(e) =>
              setReq((r) =>
                r.cohort.type === "WINDOW_SPLIT"
                  ? { ...r, cohort: { ...r.cohort, at: new Date(e.target.value).getTime() } }
                  : r,
              )
            }
          />
        )}
        <Input
          type="number"
          placeholder="support floor"
          value={req.supportFloor}
          onChange={(e) => setReq((r) => ({ ...r, supportFloor: Number(e.target.value) }))}
        />
      </div>
      <div>
        <Button size="sm" onClick={run} disabled={loading || !req.entity}>
          {loading ? "Comparing…" : "Run"}
        </Button>
      </div>
      {loading ? <LoadingTile /> : null}
      {error ? <EmptyTile heading="Not available yet" description={error} /> : null}
      {result && !error ? <LiftTable rows={result.rows} /> : null}
    </div>
  );
}

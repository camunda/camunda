/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useEffect, useState } from "react";
import { Button, Input } from "@camunda/design-system";
import { api, type ConditionsRequest, type ConditionsResponse } from "../../../lib/api";
import { formatDateTime } from "../../../lib/format";
import { EmptyTile, LoadingTile } from "../../common/EmptyTile";

function defaultRequest(): ConditionsRequest {
  return { variantHash: "", processId: "" };
}

function renderList(label: string, items: unknown[]) {
  if (!Array.isArray(items) || items.length === 0) {
    return <p className="text-sm text-neutral-foreground-muted">No {label}.</p>;
  }
  return (
    <div className="flex flex-col gap-1">
      <span className="text-xs font-medium text-neutral-foreground-muted">{label}</span>
      <ul className="flex flex-wrap gap-1.5">
        {items.map((item, i) => (
          <li key={i} className="rounded bg-neutral-background-subtle px-2 py-0.5 font-mono text-xs">
            {typeof item === "object" ? JSON.stringify(item) : String(item)}
          </li>
        ))}
      </ul>
    </div>
  );
}

/**
 * Manual "conditions" tool panel: a variant's BPMN footprint (elements/flows/firstSeen). The
 * contract doesn't detail elements'/flows' item shapes, so each renders defensively as a plain
 * chip list (a stringified object if the item is one) rather than assuming a specific record.
 */
export function ConditionsPanel({ prefill }: { prefill?: Partial<ConditionsRequest> }) {
  const [req, setReq] = useState<ConditionsRequest>({ ...defaultRequest(), ...prefill });
  const [result, setResult] = useState<ConditionsResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setReq((r) => ({ ...r, ...prefill }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [JSON.stringify(prefill)]);

  const run = () => {
    setLoading(true);
    setError(null);
    api.tools.conditions(req).then((res) => {
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
      <div className="grid grid-cols-2 gap-2">
        <Input
          placeholder="variant hash"
          value={req.variantHash}
          onChange={(e) => setReq((r) => ({ ...r, variantHash: e.target.value }))}
        />
        <Input
          placeholder="process id"
          value={req.processId}
          onChange={(e) => setReq((r) => ({ ...r, processId: e.target.value }))}
        />
      </div>
      <div>
        <Button size="sm" onClick={run} disabled={loading || !req.variantHash || !req.processId}>
          {loading ? "Loading…" : "Run"}
        </Button>
      </div>
      {loading ? <LoadingTile /> : null}
      {error ? <EmptyTile heading="Not available yet" description={error} /> : null}
      {result && !error ? (
        <div className="flex flex-col gap-3 rounded border border-border p-4">
          <span className="text-xs text-neutral-foreground-muted">
            First seen {result.firstSeen != null ? formatDateTime(result.firstSeen) : "unknown"}
          </span>
          {renderList("elements", result.elements)}
          {renderList("flows", result.flows)}
        </div>
      ) : null}
    </div>
  );
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useEffect, useState } from "react";
import { api, type DecomposeRow } from "../../../lib/api";
import { useAppData } from "../../../lib/appData";
import { formatPercent } from "../../../lib/format";
import { findDim } from "../../../lib/registryHelpers";
import { ChartCard } from "../../common/ChartCard";
import { EmptyTile, LoadingTile } from "../../common/EmptyTile";
import { ExplainLink } from "../../common/ExplainLink";

/**
 * Termination rate, derived from an entity's "state" dimension (if the registry has one) via
 * tools/decompose -- there's no dedicated outcome-rate tool in the contract. Per the spec this
 * tile is the one exception to "always show an informative empty state": when the entity has no
 * state-like dimension at all, it renders nothing (the caller simply omits it from the grid),
 * since "no termination signal available" isn't worth a permanent card in the Quality tab.
 */
export function TerminationRateTile({ entity, from, to }: { entity: string; from: number; to: number }) {
  const { entities } = useAppData();
  const stateDim = findDim(
    entities.find((e) => e.name === entity),
    ["state", "status", "outcome"],
  );

  const [rows, setRows] = useState<DecomposeRow[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(stateDim != null);

  useEffect(() => {
    if (stateDim == null) {
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(null);
    const span = to - from;
    api.tools
      .decompose({
        entity,
        measure: null,
        quantile: null,
        window: { from, to },
        baseline: { from: from - span, to: from },
        dim: stateDim,
      })
      .then((result) => {
        if (cancelled) {
          return;
        }
        if (!result.ok) {
          setError(result.message);
        } else {
          setRows(result.data.rows);
        }
        setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [entity, stateDim, from, to]);

  if (stateDim == null) {
    return null;
  }

  const total = rows?.reduce((sum, r) => sum + r.current, 0) ?? 0;
  const terminated = rows?.find((r) => r.value.toLowerCase().includes("terminat"))?.current ?? 0;
  const rate = total > 0 ? terminated / total : null;

  return (
    <ChartCard
      title="Termination rate"
      description={`Share of ${entity} in the "${stateDim}" TERMINATED state`}
      action={<ExplainLink prefill={{ entity, from, to }} />}
    >
      {loading ? (
        <LoadingTile />
      ) : error || rate == null ? (
        <EmptyTile heading="Not available yet" description={error ?? "No rows in range."} />
      ) : (
        <div className="flex h-full w-full flex-col items-center justify-center gap-3">
          <span className="text-4xl font-semibold tabular-nums">{formatPercent(rate)}</span>
          <div className="h-2.5 w-2/3 rounded bg-neutral-background-subtle">
            <div
              className="h-2.5 rounded bg-destructive"
              style={{ width: `${Math.min(100, rate * 100)}%` }}
            />
          </div>
          <span className="text-xs text-neutral-foreground-muted">
            {terminated.toLocaleString()} / {total.toLocaleString()} terminated
          </span>
        </div>
      )}
    </ChartCard>
  );
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Exhibit B's "paths (variants)" panel: one row per variant_hash, share of instances + p95
 * duration (see processStats.ts), each expandable into its element footprint via
 * `POST /api/tools/conditions` -- fetched lazily, only on first expand, and cached per row so
 * re-collapsing/re-expanding doesn't re-fetch.
 */
import { useEffect, useState } from "react";
import { Badge, Button } from "@camunda/design-system";
import { api, type ConditionsResponse } from "../../lib/api";
import { ExplainLink } from "../common/ExplainLink";
import { EmptyTile, LoadingTile } from "../common/EmptyTile";
import type { PathStat } from "./processStats";
import { formatDuration, formatPercent } from "../../lib/format";

/** "Activity_ManualCreditReview" -> "Manual Credit Review" -- duplicated in miniature from
 * components/objects/{JourneyMiniMap,ObjectDetailPage}.tsx (not exported there, and objects/ is a
 * sibling lane's territory) rather than imported. */
function prettyElementName(elementId: string): string {
  const withoutPrefix = elementId.replace(
    /^(Activity|Event|Gateway|Flow|StartEvent|EndEvent|SubProcess|Task|ServiceTask|UserTask|CallActivity)_/i,
    "",
  );
  const spaced = withoutPrefix
    .replace(/[_-]+/g, " ")
    .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
    .trim();
  return spaced.length === 0 ? elementId : spaced.replace(/\b\w/g, (c) => c.toUpperCase());
}

function shortHash(hash: string): string {
  return hash.length > 10 ? `${hash.slice(0, 10)}…` : hash;
}

function Footprint({
  processId,
  variantHash,
}: {
  processId: string;
  variantHash: string;
}) {
  const [result, setResult] = useState<ConditionsResponse | null | undefined>(undefined);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    api.tools.conditions({ processId, variantHash }).then((res) => {
      if (cancelled) {
        return;
      }
      if (!res.ok) {
        setError(res.message);
        setResult(null);
      } else {
        setResult(res.data);
      }
    });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [processId, variantHash]);

  if (result === undefined) {
    return <LoadingTile />;
  }
  if (result === null || error) {
    return <EmptyTile heading="Path footprint not available" description={error ?? undefined} />;
  }
  const elements = Array.isArray(result.elements) ? result.elements : [];
  const flows = Array.isArray(result.flows) ? result.flows : [];
  return (
    <div className="flex flex-col gap-2 pt-1">
      <div className="flex flex-wrap gap-1.5">
        {elements.length === 0 ? (
          <span className="text-xs text-neutral-foreground-muted">No elements recorded.</span>
        ) : (
          elements.map((e, i) => (
            <span key={i} className="rounded bg-neutral-background-subtle px-2 py-0.5 text-xs" title={String(e)}>
              {prettyElementName(String(e))}
            </span>
          ))
        )}
      </div>
      <span className="text-xs text-neutral-foreground-muted">{flows.length} flow(s) in this path's footprint</span>
    </div>
  );
}

export function PathsPanel({
  processId,
  stats,
  loading,
  error,
  from,
  to,
  processIdDim,
  variantDim,
}: {
  processId: string;
  stats: PathStat[] | null;
  loading: boolean;
  error: string | null;
  from: number;
  to: number;
  processIdDim: string | undefined;
  variantDim: string | undefined;
}) {
  const [expanded, setExpanded] = useState<string | null>(null);

  if (loading) {
    return <LoadingTile />;
  }
  if (error || !stats) {
    return <EmptyTile heading="Not available yet" description={error ?? "No path data."} />;
  }
  if (stats.length === 0) {
    return <EmptyTile heading="No paths in range" description="No instance_variants activity for this process/window." />;
  }

  return (
    <div className="flex flex-col gap-2">
      {stats.map((s) => (
        <div key={s.variantHash} className="flex flex-col gap-1 rounded border border-border p-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div className="flex items-center gap-2">
              <Badge variant="neutral">{formatPercent(s.share)}</Badge>
              <span className="font-mono text-xs text-neutral-foreground-muted" title={s.variantHash}>
                {shortHash(s.variantHash)}
              </span>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-xs text-neutral-foreground-muted">
                p95 {s.p95 != null ? formatDuration(s.p95) : "–"}
              </span>
              {processIdDim && variantDim ? (
                <ExplainLink
                  prefill={{
                    entity: "instance_variants",
                    measure: null,
                    quantile: 0.95,
                    filters: { [processIdDim]: processId, [variantDim]: s.variantHash },
                    from,
                    to,
                  }}
                />
              ) : null}
              <Button
                size="sm"
                variant="ghost"
                onClick={() => setExpanded(expanded === s.variantHash ? null : s.variantHash)}
              >
                {expanded === s.variantHash ? "hide footprint" : "footprint"}
              </Button>
            </div>
          </div>
          {expanded === s.variantHash ? (
            <Footprint processId={processId} variantHash={s.variantHash} />
          ) : null}
        </div>
      ))}
    </div>
  );
}

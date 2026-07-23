/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useEffect, useState } from "react";
import { Link } from "react-router";
import type { ObjectRow } from "../../../lib/api";
import { formatDateTime, formatDuration } from "../../../lib/format";
import { objectsStatsApi } from "../../../lib/objectsStatsApi";
import { ChartCard } from "../../common/ChartCard";
import { EmptyTile, LoadingTile } from "../../common/EmptyTile";

const TOP_N = 10;

/**
 * Top 10 closed objects of this type by duration, longest first -- via the sort-aware POST
 * /api/objects/list (`sort=DURATION_DESC`, `status=CLOSED`) this lane's backend piece added.
 * Degrades to an informative empty state when `object_lifecycle` isn't in the warehouse yet
 * (`status=CLOSED` then returns no rows -- same open/closed graceful-degradation rule the rest of
 * the object endpoints follow), never an error.
 */
export function TopObjectsTile({ type }: { type: string }) {
  const [rows, setRows] = useState<ObjectRow[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    setRows(null);
    objectsStatsApi
      .listSorted({ type, status: "CLOSED", limit: TOP_N, offset: 0, sort: "DURATION_DESC" })
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
  }, [type]);

  const hasOutcome = rows?.some((r) => r.outcome != null) ?? false;

  return (
    <ChartCard title="Slowest to close (top 10)" description={`Longest-lived closed ${type} objects`}>
      {loading ? (
        <LoadingTile />
      ) : error ? (
        <EmptyTile heading="Not available yet" description={error} />
      ) : !rows || rows.length === 0 ? (
        <EmptyTile
          heading="No data"
          description={`No closed ${type} objects yet (object_lifecycle may not be in the warehouse).`}
        />
      ) : (
        <div className="h-full overflow-y-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs text-neutral-foreground-muted">
                <th className="pb-2 font-normal">Object</th>
                <th className="pb-2 font-normal">Duration</th>
                <th className="pb-2 font-normal">Closed</th>
                {hasOutcome ? <th className="pb-2 font-normal">Outcome</th> : null}
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.objectId} className="border-t border-border">
                  <td className="py-1.5 pr-2">
                    <Link
                      to={`/objects/${encodeURIComponent(type)}/${encodeURIComponent(r.objectId)}`}
                      className="font-mono text-xs text-primary underline"
                    >
                      {r.objectId}
                    </Link>
                  </td>
                  <td className="py-1.5 pr-2 tabular-nums">
                    {r.durationMs != null ? formatDuration(r.durationMs) : "–"}
                  </td>
                  <td className="py-1.5 pr-2 text-xs text-neutral-foreground-muted">
                    {formatDateTime(r.closedAt)}
                  </td>
                  {hasOutcome ? <td className="py-1.5 pr-2 text-xs">{r.outcome ?? "–"}</td> : null}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </ChartCard>
  );
}

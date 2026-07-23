/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useEffect, useState } from "react";
import { Link } from "react-router";
import { api, type ObjectRow } from "../../../lib/api";
import { formatCount, formatDateTime } from "../../../lib/format";
import { ChartCard } from "../../common/ChartCard";
import { EmptyTile, LoadingTile } from "../../common/EmptyTile";

const STUCK_AGE_MS = 24 * 60 * 60 * 1000; // objects open longer than this show up as "stuck"

/**
 * The Quality tab's third tile. The spec calls this a "placeholder wired to /api/objects when
 * perspective=object" -- there is no stuck-instance detector in the process-perspective contract
 * (none of the seven tools flags a stuck instance), so:
 *  - process perspective: an explanatory placeholder (no fetch -- nothing to wire it to yet).
 *  - object perspective: a real query against POST /api/objects/list (status=OPEN), listing the
 *    oldest still-open objects as the "stuck" candidates.
 */
export function StuckNoteTile({ perspective }: { perspective: string }) {
  if (perspective === "processes") {
    return (
      <ChartCard title="Stuck instances" description="Not wired for the process perspective yet">
        <EmptyTile
          heading="Not available yet"
          description="Stuck-instance detection isn't one of the investigate tools yet -- switch to an object perspective to see long-open objects."
        />
      </ChartCard>
    );
  }
  return <StuckObjects type={perspective} />;
}

function StuckObjects({ type }: { type: string }) {
  const [rows, setRows] = useState<ObjectRow[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    api.objects.list({ type, status: "OPEN", limit: 200, offset: 0 }).then((result) => {
      if (cancelled) {
        return;
      }
      if (!result.ok) {
        setError(result.message);
      } else {
        setRows(
          [...result.data.rows]
            .sort((a, b) => new Date(a.firstSeen).getTime() - new Date(b.firstSeen).getTime())
            .slice(0, 5),
        );
      }
      setLoading(false);
    });
    return () => {
      cancelled = true;
    };
  }, [type]);

  const now = Date.now();
  const stuck = rows?.filter((r) => now - new Date(r.firstSeen).getTime() > STUCK_AGE_MS) ?? [];

  return (
    <ChartCard
      title="Stuck objects"
      description={`Open ${type} objects older than 24h, oldest first`}
    >
      {loading ? (
        <LoadingTile />
      ) : error ? (
        <EmptyTile heading="Not available yet" description={error} />
      ) : stuck.length === 0 ? (
        <EmptyTile heading="Nothing stuck" description="No open objects older than 24h." />
      ) : (
        <div className="flex h-full flex-col gap-2 overflow-y-auto text-sm">
          {stuck.map((r) => (
            <Link
              key={r.objectId}
              to={`/objects/${encodeURIComponent(type)}/${encodeURIComponent(r.objectId)}`}
              className="flex items-center justify-between rounded border border-border px-3 py-2 hover:bg-neutral-background-subtle"
            >
              <span className="truncate font-mono text-xs">{r.objectId}</span>
              <span className="shrink-0 text-xs text-neutral-foreground-muted">
                since {formatDateTime(r.firstSeen)} · {formatCount(r.nInstances)} instances
              </span>
            </Link>
          ))}
        </div>
      )}
    </ChartCard>
  );
}

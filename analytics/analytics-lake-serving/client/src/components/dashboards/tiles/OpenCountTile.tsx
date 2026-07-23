/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useEffect, useState } from "react";
import { Link } from "react-router";
import { api } from "../../../lib/api";
import { formatCount } from "../../../lib/format";
import { ChartCard } from "../../common/ChartCard";
import { EmptyTile, LoadingTile } from "../../common/EmptyTile";

/**
 * "Open count (list endpoint)" for the object KPI tab: POST /api/objects/list has no dedicated
 * count response, so this pages through with a generous limit and reports rows.length -- an exact
 * count up to that page size, an (annotated) lower bound beyond it.
 */
const PAGE_SIZE = 1000;

export function OpenCountTile({ type }: { type: string }) {
  const [count, setCount] = useState<number | null>(null);
  const [atLimit, setAtLimit] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    api.objects.list({ type, status: "OPEN", limit: PAGE_SIZE, offset: 0 }).then((result) => {
      if (cancelled) {
        return;
      }
      if (!result.ok) {
        setError(result.message);
      } else {
        setCount(result.data.rows.length);
        setAtLimit(result.data.rows.length >= PAGE_SIZE);
      }
      setLoading(false);
    });
    return () => {
      cancelled = true;
    };
  }, [type]);

  return (
    <ChartCard title="Open count" description={`Currently-open ${type} objects`}>
      {loading ? (
        <LoadingTile />
      ) : error || count == null ? (
        <EmptyTile heading="Not available yet" description={error ?? "No data."} />
      ) : (
        <div className="flex h-full w-full flex-col items-center justify-center gap-2">
          <span className="text-4xl font-semibold tabular-nums">
            {atLimit ? `${formatCount(count)}+` : formatCount(count)}
          </span>
          <Link
            to={`/objects/${encodeURIComponent(type)}?status=OPEN`}
            className="text-xs text-primary underline"
          >
            View list
          </Link>
        </div>
      )}
    </ChartCard>
  );
}

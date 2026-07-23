/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useEffect, useState } from "react";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { chartColor } from "../../../lib/chartColors";
import { formatCount } from "../../../lib/format";
import { objectsStatsApi, type ByProcessRow } from "../../../lib/objectsStatsApi";
import { ChartCard } from "../../common/ChartCard";
import { EmptyTile, LoadingTile } from "../../common/EmptyTile";

/**
 * "Which processes touch/create these objects": a horizontal bar per process, ranked by how many
 * distinct objects of this type its instances have sighted -- via POST /api/objects/stats'
 * `byProcess` (backed by the `objects` dictionary view, degrades to an empty list rather than an
 * error when that view isn't in the warehouse yet).
 */
export function BirthsByProcessTile({ type }: { type: string }) {
  const [rows, setRows] = useState<ByProcessRow[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    setRows(null);
    objectsStatsApi.stats({ type }).then((result) => {
      if (cancelled) {
        return;
      }
      if (!result.ok) {
        setError(result.message);
      } else {
        setRows(result.data.byProcess);
      }
      setLoading(false);
    });
    return () => {
      cancelled = true;
    };
  }, [type]);

  return (
    <ChartCard
      title="Throughput by process"
      description={`${type} objects sighted, by process`}
    >
      {loading ? (
        <LoadingTile />
      ) : error || !rows ? (
        <EmptyTile
          heading="Not available yet"
          description={error ?? "objects isn't in the warehouse yet."}
        />
      ) : rows.length === 0 ? (
        <EmptyTile
          heading="No data"
          description={`No process has sighted a ${type} object yet.`}
        />
      ) : (
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={rows} layout="vertical" margin={{ top: 8, right: 24, bottom: 4, left: 8 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border, #e5e7eb)" />
            <XAxis type="number" tick={{ fontSize: 12 }} tickFormatter={formatCount} allowDecimals={false} />
            <YAxis type="category" dataKey="processId" width={130} tick={{ fontSize: 11 }} interval={0} />
            <Tooltip formatter={(value) => [formatCount(Number(value)), "Objects"]} />
            <Bar dataKey="n" fill={chartColor(0)} radius={[0, 4, 4, 0]} />
          </BarChart>
        </ResponsiveContainer>
      )}
    </ChartCard>
  );
}

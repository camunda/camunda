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
import { objectsStatsApi, type RelationFanoutRow } from "../../../lib/objectsStatsApi";
import { ChartCard } from "../../common/ChartCard";
import { EmptyTile, LoadingTile } from "../../common/EmptyTile";

/**
 * "Children per object" distribution: how many of this type's parent objects have exactly k
 * children in `object_relations` (e.g. line items per order) -- via POST /api/objects/stats'
 * `relationFanout`, ordered by k ascending. Degrades to an empty list rather than an error when
 * `object_relations` isn't in the warehouse yet.
 */
export function RelationFanoutTile({ type }: { type: string }) {
  const [rows, setRows] = useState<RelationFanoutRow[] | null>(null);
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
        setRows(result.data.relationFanout);
      }
      setLoading(false);
    });
    return () => {
      cancelled = true;
    };
  }, [type]);

  return (
    <ChartCard title="Children per object" description={`Relation fanout for ${type} objects`}>
      {loading ? (
        <LoadingTile />
      ) : error || !rows ? (
        <EmptyTile
          heading="Not available yet"
          description={error ?? "object_relations isn't in the warehouse yet."}
        />
      ) : rows.length === 0 ? (
        <EmptyTile
          heading="No data"
          description={`No ${type} object has a recorded relation yet.`}
        />
      ) : (
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={rows} margin={{ top: 8, right: 16, bottom: 4, left: 8 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border, #e5e7eb)" />
            <XAxis
              dataKey="children"
              tick={{ fontSize: 12 }}
              label={{ value: "Children", position: "insideBottom", offset: -2, fontSize: 11 }}
            />
            <YAxis allowDecimals={false} width={48} tick={{ fontSize: 12 }} tickFormatter={formatCount} />
            <Tooltip
              formatter={(value) => [formatCount(Number(value)), "Objects"]}
              labelFormatter={(label) => `${label} children`}
            />
            <Bar dataKey="n" fill={chartColor(3)} radius={[4, 4, 0, 0]} />
          </BarChart>
        </ResponsiveContainer>
      )}
    </ChartCard>
  );
}

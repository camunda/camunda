/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useEffect, useState } from "react";
import { Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip } from "recharts";
import { chartColor } from "../../../lib/chartColors";
import { formatCount } from "../../../lib/format";
import { objectsStatsApi, type OutcomeRow } from "../../../lib/objectsStatsApi";
import { ChartCard } from "../../common/ChartCard";
import { EmptyTile, LoadingTile } from "../../common/EmptyTile";

/**
 * Closing-outcome split for this object type, as a donut -- via POST /api/objects/stats'
 * `outcomes` (backed by `object_lifecycle`, ordered by count descending). Replaces the previous
 * speculative DecomposeByDimTile("Outcome split") on `object_cohorts`, which probed for an
 * "outcome"/"result"/"status" dimension that entity doesn't actually expose; this reads the real
 * closing outcome straight off the lifecycle fact table instead.
 */
export function OutcomeSplitTile({ type }: { type: string }) {
  const [rows, setRows] = useState<OutcomeRow[] | null>(null);
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
        setRows(result.data.outcomes);
      }
      setLoading(false);
    });
    return () => {
      cancelled = true;
    };
  }, [type]);

  return (
    <ChartCard title="Outcome split" description={`${type} objects by closing outcome`}>
      {loading ? (
        <LoadingTile />
      ) : error || !rows ? (
        <EmptyTile
          heading="Not available yet"
          description={error ?? "object_lifecycle isn't in the warehouse yet."}
        />
      ) : rows.length === 0 ? (
        <EmptyTile heading="No data" description={`No closed ${type} objects with an outcome yet.`} />
      ) : (
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie
              data={rows}
              dataKey="n"
              nameKey="outcome"
              innerRadius="55%"
              outerRadius="85%"
              paddingAngle={rows.length > 1 ? 1 : 0}
              stroke="none"
            >
              {rows.map((row, i) => (
                <Cell key={row.outcome} fill={chartColor(i)} />
              ))}
            </Pie>
            <Tooltip formatter={(value, name) => [formatCount(Number(value)), String(name)]} />
            <Legend />
          </PieChart>
        </ResponsiveContainer>
      )}
    </ChartCard>
  );
}

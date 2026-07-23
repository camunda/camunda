/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { PROCESSES_PERSPECTIVE, useAppData } from "../../lib/appData";
import { formatDuration } from "../../lib/format";
import { objectTypeFilter } from "../../lib/registryHelpers";
import type { DashboardRange } from "./DashboardRangePicker";
import { DecomposeByDimTile } from "./tiles/DecomposeByDimTile";
import { SeriesTile } from "./tiles/SeriesTile";
import { TopObjectsTile } from "./tiles/TopObjectsTile";

export function PerformanceTab({
  perspective,
  range,
}: {
  perspective: string;
  range: DashboardRange;
}) {
  const { from, to, grainMinutes } = range;
  const { entities } = useAppData();

  if (perspective === PROCESSES_PERSPECTIVE) {
    return (
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <SeriesTile
          title="Duration percentiles"
          description="p50 / p95 process-instance duration trend"
          series={[
            { entity: "instances", quantile: 0.5, label: "p50", colorIndex: 2 },
            { entity: "instances", quantile: 0.95, label: "p95", colorIndex: 1 },
          ]}
          from={from}
          to={to}
          grainMinutes={grainMinutes}
          chartType="line"
          valueFormatter={formatDuration}
        />
        <DecomposeByDimTile
          title="Per-element p95"
          description="p95 activity duration by element"
          entity="activities"
          quantile={0.95}
          dimCandidates={["elementId", "element_id", "element"]}
          from={from}
          to={to}
          valueFormatter={formatDuration}
        />
        <DecomposeByDimTile
          title="Slowest variants (top 10)"
          description="p95 duration by execution variant"
          entity="instance_variants"
          quantile={0.95}
          dimCandidates={["variantHash", "variant_hash", "variant"]}
          from={from}
          to={to}
          topN={10}
          valueFormatter={formatDuration}
        />
        <DecomposeByDimTile
          title="Branch shares"
          description="Flow-taken counts by branch (source → target when available)"
          entity="flows"
          measure="cnt"
          dimCandidates={["flowId", "flow_id", "flow"]}
          from={from}
          to={to}
        />
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
      <SeriesTile
        title="Time to close"
        description={`p95 time-to-close trend for ${perspective} objects`}
        series={[
          {
            entity: "object_cohorts",
            quantile: 0.95,
            label: "p95",
            colorIndex: 1,
            filters: objectTypeFilter(entities, "object_cohorts", perspective),
          },
        ]}
        from={from}
        to={to}
        grainMinutes={grainMinutes}
        chartType="line"
        valueFormatter={formatDuration}
      />
      <TopObjectsTile type={perspective} />
    </div>
  );
}

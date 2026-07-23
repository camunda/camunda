/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { PROCESSES_PERSPECTIVE, useAppData } from "../../lib/appData";
import { findMeasure, objectTypeFilter } from "../../lib/registryHelpers";
import { ChartCard } from "../common/ChartCard";
import { EmptyTile } from "../common/EmptyTile";
import type { DashboardRange } from "./DashboardRangePicker";
import { DecomposeByDimTile } from "./tiles/DecomposeByDimTile";
import { ShareTrendTile } from "./tiles/ShareTrendTile";
import { StuckNoteTile } from "./tiles/StuckNoteTile";
import { TerminationRateTile } from "./tiles/TerminationRateTile";

/** null/nonfinite-rate by variable: resolves which of "nullRate"/"nonFiniteRate" (or similar) the
 * registry actually exposes on variable_profiles before rendering, since the two are easily
 * confused and a wrong guess would mislabel the chart rather than just being empty. */
function NullRateTile({ from, to }: { from: number; to: number }) {
  const { entities } = useAppData();
  const entity = entities.find((e) => e.name === "variable_profiles");
  const measure = findMeasure(entity, ["nullrate", "null_rate", "nonfinite", "non_finite", "invalid"]);

  if (entity != null && measure == null) {
    return (
      <ChartCard title="Null / non-finite rate" description="By variable">
        <EmptyTile
          heading="Not available yet"
          description="variable_profiles has no null/non-finite-rate measure in the registry yet."
        />
      </ChartCard>
    );
  }

  return (
    <DecomposeByDimTile
      title="Null / non-finite rate"
      description="Share of null or non-finite values, by variable"
      entity="variable_profiles"
      measure={measure ?? null}
      dimCandidates={["varName", "var_name", "variable"]}
      from={from}
      to={to}
    />
  );
}

export function QualityTab({ perspective, range }: { perspective: string; range: DashboardRange }) {
  const { from, to, grainMinutes } = range;
  const { entities } = useAppData();

  if (perspective === PROCESSES_PERSPECTIVE) {
    return (
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <TerminationRateTile entity="instances" from={from} to={to} />
        <StuckNoteTile perspective={perspective} />
        <NullRateTile from={from} to={to} />
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
      <ShareTrendTile
        title="Survival curve"
        description={`Share of ${perspective} objects closed, by age`}
        entity="object_cohorts"
        totalMeasureCandidates={["started", "total", "cnt", "born"]}
        bands={[{ candidates: ["closed", "1d", "day"], label: "Closed", colorIndex: 2 }]}
        from={from}
        to={to}
        grainMinutes={grainMinutes}
        mode="line"
        filters={objectTypeFilter(entities, "object_cohorts", perspective)}
      />
      <StuckNoteTile perspective={perspective} />
    </div>
  );
}

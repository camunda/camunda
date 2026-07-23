/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { PROCESSES_PERSPECTIVE } from "../../lib/appData";
import type { DashboardRange } from "./DashboardRangePicker";
import { DecomposeByDimTile } from "./tiles/DecomposeByDimTile";
import { OpenCountTile } from "./tiles/OpenCountTile";
import { SeriesTile } from "./tiles/SeriesTile";
import { ShareTrendTile } from "./tiles/ShareTrendTile";

export function KpiTab({ perspective, range }: { perspective: string; range: DashboardRange }) {
  const { from, to, grainMinutes } = range;

  if (perspective === PROCESSES_PERSPECTIVE) {
    return (
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <SeriesTile
          title="Started vs. completed"
          description="Instances started and completed per window"
          series={[
            { entity: "instance_starts", measure: "cnt", label: "Started", colorIndex: 3 },
            { entity: "instances", measure: "cnt", label: "Completed", colorIndex: 2 },
          ]}
          from={from}
          to={to}
          grainMinutes={grainMinutes}
        />
        <ShareTrendTile
          title="Cohort survival"
          description="Share of each start cohort completed within 1h / 1d"
          entity="instance_cohorts"
          totalMeasureCandidates={["started", "total", "cnt"]}
          bands={[
            { candidates: ["1h", "hour"], label: "≤ 1h", colorIndex: 2 },
            { candidates: ["1d", "day"], label: "≤ 1d", colorIndex: 0 },
          ]}
          from={from}
          to={to}
          grainMinutes={grainMinutes}
        />
        <DecomposeByDimTile
          title="Throughput by process"
          description="Instances started per process, this window"
          entity="instance_starts"
          dimCandidates={["bpmnProcessId", "processId", "process"]}
          from={from}
          to={to}
        />
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
      <SeriesTile
        title="Born vs. closed"
        description={`${perspective} objects born and closed per window`}
        series={[
          {
            entity: "objects_born",
            measure: "cnt",
            label: "Born",
            colorIndex: 3,
            filters: { type: perspective },
          },
          {
            entity: "object_cohorts",
            measure: "cnt",
            label: "Closed",
            colorIndex: 2,
            filters: { type: perspective },
          },
        ]}
        from={from}
        to={to}
        grainMinutes={grainMinutes}
      />
      <OpenCountTile type={perspective} />
    </div>
  );
}

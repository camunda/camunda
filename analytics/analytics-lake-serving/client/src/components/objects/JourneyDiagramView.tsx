/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useMemo } from "react";
import type { JourneyActivity, ObjectSighting } from "../../lib/api";
import { chartColor } from "../../lib/chartColors";
import { BpmnDiagramLane } from "./BpmnDiagramLane";

interface Lane {
  processId: string;
  version: number;
  color: string;
  visitCounts: Map<string, number>;
  firstSeenMs: number;
}

/**
 * One BPMN diagram per (processId, version) the journey actually touched -- almost always just
 * one, but a call-activity chain can span more than one process definition. `sightings` (which
 * carry `version`; the flat activity list doesn't) resolve each activity's instance back to the
 * definition version it ran against.
 */
export function JourneyDiagramView({
  activities,
  sightings,
}: {
  activities: JourneyActivity[];
  sightings: ObjectSighting[];
}) {
  const lanes = useMemo<Lane[]>(() => {
    const versionByInstance = new Map<number, number>();
    for (const s of sightings) {
      versionByInstance.set(s.instanceKey, s.version);
    }

    const laneMap = new Map<
      string,
      { processId: string; version: number; visitCounts: Map<string, number>; firstSeenMs: number }
    >();
    for (const a of activities) {
      const version = versionByInstance.get(a.instanceKey) ?? 1;
      const key = `${a.processId}::${version}`;
      let lane = laneMap.get(key);
      if (!lane) {
        lane = { processId: a.processId, version, visitCounts: new Map(), firstSeenMs: new Date(a.startedAt).getTime() };
        laneMap.set(key, lane);
      }
      lane.visitCounts.set(a.elementId, (lane.visitCounts.get(a.elementId) ?? 0) + 1);
      lane.firstSeenMs = Math.min(lane.firstSeenMs, new Date(a.startedAt).getTime());
    }

    const processIds = [...new Set([...laneMap.values()].map((l) => l.processId))].sort();
    return [...laneMap.values()]
      .sort((a, b) => a.firstSeenMs - b.firstSeenMs)
      .map((l) => ({ ...l, color: chartColor(processIds.indexOf(l.processId)) }));
  }, [activities, sightings]);

  if (lanes.length === 0) {
    return <p className="text-sm text-neutral-foreground-muted">No activities recorded.</p>;
  }

  return (
    <div className="flex flex-col gap-4">
      {lanes.map((lane) => (
        <BpmnDiagramLane
          key={`${lane.processId}::${lane.version}`}
          processId={lane.processId}
          version={lane.version}
          color={lane.color}
          visitCounts={lane.visitCounts}
        />
      ))}
    </div>
  );
}

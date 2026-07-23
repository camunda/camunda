/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Exhibit B's "BPMN heatmap": the selected version's diagram (bpmn-js NavigatedViewer, same
 * pan/zoom-only viewer components/objects/BpmnDiagramLane.tsx uses -- duplicated here rather than
 * imported since that component's props are shaped for a journey's visit-count overlay, not a
 * p95/count heatmap, and objects/ is a sibling lane's territory) with each element colored by a
 * simple 3-step p95 scale (tercile rank among elements that have a p95 at all) and badged with its
 * visit count. Elements the stats never mention (i.e. never executed in this window) are left
 * uncolored -- an unstyled node is real information ("nothing routed through here"), not a loading
 * gap.
 */
import { useEffect, useRef } from "react";
import "bpmn-js/dist/assets/diagram-js.css";
import "bpmn-js/dist/assets/bpmn-js.css";
import "bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css";
import NavigatedViewer from "bpmn-js/lib/NavigatedViewer";
import type Canvas from "diagram-js/lib/core/Canvas";
import type ElementRegistry from "diagram-js/lib/core/ElementRegistry";
import type Overlays from "diagram-js/lib/features/overlays/Overlays";
import { Card, CardContent, CardHeader, CardTitle } from "@camunda/design-system";
import { formatDuration } from "../../lib/format";
import { EmptyTile, LoadingTile } from "../common/EmptyTile";
import type { ActivityStat } from "./processStats";

const DIAGRAM_HEIGHT = 380;
const HEAT_CLASSES = ["heat-low", "heat-mid", "heat-high"] as const;
// Muted-green / amber / red-orange -- ordinal, not the design-system's semantic success/danger
// tokens, since "high p95" isn't necessarily "bad" outside its own process's context.
const HEAT_COLORS = ["#2E7D4F", "#D9A62E", "#B3382C"];

/** Tercile rank (0/1/2) of each element with a known p95, by index position among p95s sorted
 * ascending -- robust to skew (a handful of very slow outliers don't compress the other two bands
 * the way min/max-range thirds would). Elements without a p95 are absent from the returned map. */
function terciles(stats: ActivityStat[]): Map<string, number> {
  const withP95 = stats.filter((s): s is ActivityStat & { p95: number } => s.p95 != null);
  const sorted = [...withP95].sort((a, b) => a.p95 - b.p95);
  const n = sorted.length;
  const rank = new Map<string, number>();
  sorted.forEach((s, i) => {
    const band = n <= 1 ? 1 : Math.min(2, Math.floor((i / n) * 3));
    rank.set(s.elementId, band);
  });
  return rank;
}

export function ProcessHeatmapCard({
  processId,
  version,
  bpmnXml,
  xmlNotAvailable,
  stats,
}: {
  processId: string;
  version: number;
  bpmnXml: string | null;
  xmlNotAvailable: boolean;
  stats: ActivityStat[] | null;
}) {
  const containerRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!bpmnXml || !containerRef.current || !stats) {
      return;
    }
    const container = containerRef.current;
    const viewer = new NavigatedViewer({ container });
    let cancelled = false;
    const rank = terciles(stats);
    const byElement = new Map(stats.map((s) => [s.elementId, s]));

    viewer
      .importXML(bpmnXml)
      .then(() => {
        if (cancelled) {
          return;
        }
        const canvas = viewer.get<Canvas>("canvas");
        const elementRegistry = viewer.get<ElementRegistry>("elementRegistry");
        const overlays = viewer.get<Overlays>("overlays");
        canvas.zoom("fit-viewport");

        for (const [elementId, stat] of byElement) {
          if (elementRegistry.get(elementId) === undefined) {
            continue; // the stats mention an element this diagram version doesn't have
          }
          const band = rank.get(elementId);
          if (band != null) {
            canvas.addMarker(elementId, HEAT_CLASSES[band]);
          }
          if (stat.count > 0) {
            const badge = document.createElement("div");
            badge.className = "heatmap-badge";
            badge.textContent = stat.p95 != null ? `${stat.count} · ${formatDuration(stat.p95)}` : String(stat.count);
            overlays.add(elementId, "heatmap-badge", { html: badge, position: { top: -10, right: -10 } });
          }
        }
      })
      .catch(() => {
        // A malformed/incompatible BPMN XML degrades to an empty diagram pane, not a page crash.
      });

    return () => {
      cancelled = true;
      viewer.destroy();
    };
  }, [bpmnXml, stats]);

  return (
    <Card>
      <CardHeader>
        <CardTitle>
          {processId} · v{version} · BPMN heatmap
        </CardTitle>
      </CardHeader>
      <CardContent>
        {xmlNotAvailable ? (
          <EmptyTile
            heading="No diagram for this version"
            description="GET /api/definitions had nothing for this process/version -- showing paths instead."
          />
        ) : !bpmnXml ? (
          <LoadingTile />
        ) : (
          <>
            <style>{`
              .process-heatmap .djs-element.heat-low .djs-visual > :is(rect, circle, polygon, path) {
                stroke: ${HEAT_COLORS[0]}; stroke-width: 2.5px;
              }
              .process-heatmap .djs-element.heat-mid .djs-visual > :is(rect, circle, polygon, path) {
                stroke: ${HEAT_COLORS[1]}; stroke-width: 2.5px;
              }
              .process-heatmap .djs-element.heat-high .djs-visual > :is(rect, circle, polygon, path) {
                stroke: ${HEAT_COLORS[2]}; stroke-width: 3px;
              }
              .process-heatmap .heatmap-badge {
                background: var(--card, #fff); border: 1px solid var(--line, #ccc);
                color: inherit; font-size: 10px; font-weight: 600; line-height: 1.4;
                border-radius: 999px; padding: 1px 6px; white-space: nowrap;
              }
            `}</style>
            <div
              ref={containerRef}
              className="process-heatmap"
              style={{ height: DIAGRAM_HEIGHT, width: "100%" }}
            />
            <p className="mt-2 text-xs text-neutral-foreground-muted">
              Elements shaded by p95 duration (green → amber → red), badged with visit count · p95.
              Uncolored elements had no completions in this window. Stats aggregate across every
              deployed version ({"activities"} has no version dimension) -- only the diagram shape
              itself follows the version chip.
            </p>
          </>
        )}
      </CardContent>
    </Card>
  );
}

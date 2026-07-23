/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useEffect, useRef, useState } from "react";
import "bpmn-js/dist/assets/diagram-js.css";
import "bpmn-js/dist/assets/bpmn-js.css";
import "bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css";
import NavigatedViewer from "bpmn-js/lib/NavigatedViewer";
import type Canvas from "diagram-js/lib/core/Canvas";
import type ElementRegistry from "diagram-js/lib/core/ElementRegistry";
import type Overlays from "diagram-js/lib/features/overlays/Overlays";
import { Card, CardContent, CardHeader, CardTitle } from "@camunda/design-system";
import { definitionsApi } from "../../lib/definitionsApi";
import { EmptyTile, LoadingTile } from "../common/EmptyTile";

const DIAGRAM_HEIGHT = 360;

/** "orderProcess" v3 -> "orderprocess-v3-a1b2c3": a CSS-safe, collision-resistant class token so
 * this lane's marker/badge styling never bleeds into a sibling lane rendered right below it. */
function laneClass(processId: string, version: number): string {
  const safe = processId.replace(/[^a-zA-Z0-9]/g, "-").toLowerCase();
  return `journey-lane-${safe}-v${version}`;
}

/**
 * One process definition's BPMN diagram (bpmn-js NavigatedViewer -- pan/zoom, no editing), with
 * every journey-visited element marked and a visit-count badge where it was seen more than once.
 * Fetches its own XML lazily on mount (only ever mounted once the Diagram view is selected) and
 * renders nothing if the definition can't be found -- an older/missing process_definitions row
 * degrades to "no diagram for this lane" rather than an error.
 */
export function BpmnDiagramLane({
  processId,
  version,
  color,
  visitCounts,
}: {
  processId: string;
  version: number;
  color: string;
  visitCounts: Map<string, number>;
}) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [xml, setXml] = useState<string | null>(null);
  const [notAvailable, setNotAvailable] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setXml(null);
    setNotAvailable(false);
    definitionsApi.get(processId, version).then((result) => {
      if (cancelled) {
        return;
      }
      if (result.ok) {
        setXml(result.data.bpmnXml);
      } else {
        setNotAvailable(true);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [processId, version]);

  useEffect(() => {
    if (!xml || !containerRef.current) {
      return;
    }
    const container = containerRef.current;
    const viewer = new NavigatedViewer({ container });
    let cancelled = false;

    viewer
      .importXML(xml)
      .then(() => {
        if (cancelled) {
          return;
        }
        const canvas = viewer.get<Canvas>("canvas");
        const elementRegistry = viewer.get<ElementRegistry>("elementRegistry");
        const overlays = viewer.get<Overlays>("overlays");
        canvas.zoom("fit-viewport");

        for (const [elementId, count] of visitCounts) {
          if (elementRegistry.get(elementId) === undefined) {
            continue; // journey touched an element this definition version no longer has
          }
          canvas.addMarker(elementId, "journey-visited");
          if (count > 1) {
            const badge = document.createElement("div");
            badge.className = "journey-visit-badge";
            badge.textContent = String(count);
            overlays.add(elementId, "journey-badge", { html: badge, position: { top: -8, right: -8 } });
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
  }, [xml, visitCounts]);

  const cssClass = laneClass(processId, version);

  return (
    <Card>
      <CardHeader>
        <CardTitle>
          {processId} · v{version}
        </CardTitle>
      </CardHeader>
      <CardContent>
        {notAvailable ? (
          <EmptyTile heading="No diagram for this process version" description="GET /api/definitions had nothing for this lane." />
        ) : !xml ? (
          <LoadingTile />
        ) : (
          <>
            <style>{`
              .${cssClass} .djs-element.journey-visited .djs-visual > :is(rect, circle, polygon, path) {
                stroke: ${color};
                stroke-width: 2.5px;
              }
              .${cssClass} .journey-visit-badge {
                background: ${color};
                color: white;
                font-size: 10px;
                font-weight: 600;
                line-height: 1;
                border-radius: 999px;
                min-width: 16px;
                height: 16px;
                display: flex;
                align-items: center;
                justify-content: center;
                padding: 0 4px;
              }
            `}</style>
            <div ref={containerRef} className={cssClass} style={{ height: DIAGRAM_HEIGHT, width: "100%" }} />
          </>
        )}
      </CardContent>
    </Card>
  );
}

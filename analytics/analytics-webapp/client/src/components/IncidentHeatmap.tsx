/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import {
  Button,
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@camunda/design-system";
// @ts-expect-error — bpmn-js ships no types for this entry
import NavigatedViewer from "bpmn-js/lib/NavigatedViewer";
import "bpmn-js/dist/assets/diagram-js.css";
import "bpmn-js/dist/assets/bpmn-js.css";
import "bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css";
import { useEffect, useRef, useState } from "react";
import type { IncidentFlowNode } from "../lib/api";
import { formatCount } from "../lib/format";
import { getHeatmap } from "../lib/optimizeHeatmap";

type Metric = "raised" | "open";

const METRICS: { key: Metric; label: string }[] = [
  { key: "raised", label: "Raised (in range)" },
  { key: "open", label: "Open (now)" },
];

function escapeHtml(s: string): string {
  return s.replace(
    /[&<>"]/g,
    (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]!,
  );
}

function tooltipHtml(e: IncidentFlowNode): HTMLElement {
  const div = document.createElement("div");
  div.style.cssText =
    "font:12px/1.5 system-ui;background:#111827;color:#fff;padding:8px 10px;border-radius:6px;" +
    "box-shadow:0 4px 14px rgba(0,0,0,0.28);white-space:nowrap;transform:translateY(8px);" +
    "pointer-events:none;";
  div.innerHTML =
    `<div style="font-weight:600">${escapeHtml(e.elementId)}</div>` +
    `<div>Raised: <b>${formatCount(e.raised)}</b></div>` +
    `<div>Open now: <b>${formatCount(e.open)}</b></div>`;
  return div;
}

/**
 * The BPMN diagram with an incident heatmap overlay — Optimize's incident-frequency report, which
 * colours the flow node that raised the incidents. Toggle between incidents raised in range and
 * currently-open incidents; the heat uses the same Optimize {@code getHeatmap} as the flow-node
 * duration heatmap. (Incident duration by node is a separate metric, added later.)
 */
export function IncidentHeatmap({
  process,
  incidents,
}: {
  process: string;
  incidents: IncidentFlowNode[];
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const viewerRef = useRef<any>(null);
  const heatRef = useRef<SVGImageElement | null>(null);
  const [ready, setReady] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [metric, setMetric] = useState<Metric>("raised");

  useEffect(() => {
    let cancelled = false;
    setReady(false);
    setError(null);
    const viewer = new NavigatedViewer({ container: containerRef.current });
    viewerRef.current = viewer;
    heatRef.current = null;

    (async () => {
      try {
        const res = await fetch(`/api/dashboard/diagram?process=${encodeURIComponent(process)}`);
        if (!res.ok) {
          throw new Error(res.status === 404 ? "No diagram deployed yet." : `HTTP ${res.status}`);
        }
        let xml = await res.text();
        if (!xml.includes("BPMNDiagram")) {
          const { layoutProcess } = await import("bpmn-auto-layout");
          xml = await layoutProcess(xml);
        }
        await viewer.importXML(xml);
        if (cancelled) {
          return;
        }
        viewer.get("canvas").zoom("fit-viewport", "auto");
        setReady(true);
      } catch (e: unknown) {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : String(e));
        }
      }
    })();

    return () => {
      cancelled = true;
      try {
        viewer.destroy();
      } catch {
        // already gone
      }
      viewerRef.current = null;
    };
  }, [process]);

  useEffect(() => {
    const viewer = viewerRef.current;
    if (!viewer || !ready) {
      return;
    }
    const values: Record<string, number> = {};
    for (const e of incidents) {
      values[e.elementId] = metric === "raised" ? e.raised : e.open;
    }

    const viewport = viewer.get("canvas")._viewport as SVGGElement;
    if (heatRef.current && heatRef.current.parentNode) {
      heatRef.current.parentNode.removeChild(heatRef.current);
    }
    try {
      const node = getHeatmap(viewer, values);
      viewport.appendChild(node);
      heatRef.current = node;
    } catch (e) {
      // eslint-disable-next-line no-console
      console.error("incident heatmap render failed", e);
    }

    const eventBus = viewer.get("eventBus");
    const overlays = viewer.get("overlays");
    const byId = new Map(incidents.map((e) => [e.elementId, e] as const));
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const onHover = (evt: any) => {
      const e = byId.get(evt.element?.id);
      if (!e) {
        return;
      }
      overlays.remove({ type: "incident-tooltip" });
      overlays.add(e.elementId, "incident-tooltip", {
        position: { bottom: 0, left: 0 },
        html: tooltipHtml(e),
      });
    };
    const onOut = () => overlays.remove({ type: "incident-tooltip" });
    eventBus.on("element.hover", onHover);
    eventBus.on("element.out", onOut);

    return () => {
      eventBus.off("element.hover", onHover);
      eventBus.off("element.out", onOut);
      try {
        overlays.remove({ type: "incident-tooltip" });
      } catch {
        // viewer gone
      }
    };
  }, [ready, incidents, metric]);

  return (
    <Card>
      <CardHeader>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <CardTitle>Incident heatmap</CardTitle>
            <CardDescription>Incidents by flow node, on the process diagram</CardDescription>
          </div>
          <div className="flex gap-1">
            {METRICS.map((m) => (
              <Button
                key={m.key}
                size="sm"
                variant={metric === m.key ? "default" : "secondary"}
                onClick={() => setMetric(m.key)}
              >
                {m.label}
              </Button>
            ))}
          </div>
        </div>
      </CardHeader>
      <CardContent>
        {error ? <p className="py-8 text-center text-neutral-foreground-muted">{error}</p> : null}
        <div
          ref={containerRef}
          className="h-[440px] w-full overflow-hidden rounded border border-border bg-white"
        />
        <div className="mt-3 flex items-center gap-2 text-xs text-neutral-foreground-muted">
          <span>low</span>
          <span
            className="h-2 w-40 rounded"
            style={{
              background:
                "linear-gradient(to right, rgb(0,0,255), rgb(0,255,0), rgb(255,255,0), rgb(255,0,0))",
            }}
          />
          <span>high</span>
          <span className="ml-2">
            colored by {METRICS.find((m) => m.key === metric)?.label.toLowerCase()}
          </span>
        </div>
      </CardContent>
    </Card>
  );
}

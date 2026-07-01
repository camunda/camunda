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
import type { ElementDuration } from "../lib/api";
import { formatCount, formatDuration } from "../lib/format";
import { getHeatmap } from "../lib/optimizeHeatmap";

type Metric = "count" | "avg" | "p90";

const METRICS: { key: Metric; label: string }[] = [
  { key: "count", label: "Count" },
  { key: "avg", label: "Avg duration" },
  { key: "p90", label: "p90 duration" },
];

function value(e: ElementDuration, m: Metric): number {
  return m === "count" ? e.executedCount : m === "avg" ? e.avgMs : e.p90Ms;
}

function escapeHtml(s: string): string {
  return s.replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]!);
}

// Hover tooltip content (Optimize-style): the element, and its full set of metrics.
function tooltipHtml(e: ElementDuration): HTMLElement {
  const div = document.createElement("div");
  div.style.cssText =
    "font:12px/1.5 system-ui;background:#111827;color:#fff;padding:8px 10px;border-radius:6px;" +
    "box-shadow:0 4px 14px rgba(0,0,0,0.28);white-space:nowrap;transform:translateY(8px);" +
    "pointer-events:none;";
  div.innerHTML =
    `<div style="font-weight:600">${escapeHtml(e.elementId)}</div>` +
    `<div style="opacity:.65;font-size:11px;margin-bottom:5px">${escapeHtml(e.elementType)}</div>` +
    `<div>Executed: <b>${formatCount(e.executedCount)}</b></div>` +
    `<div>Avg <b>${formatDuration(e.avgMs)}</b> · p50 <b>${formatDuration(e.p50Ms)}</b></div>` +
    `<div>p90 <b>${formatDuration(e.p90Ms)}</b> · max <b>${formatDuration(e.maxMs)}</b></div>`;
  return div;
}

/**
 * Renders the process's BPMN diagram (bpmn-js) with a heatmap overlay of a per-element metric,
 * rendered by Optimize's own {@code getHeatmap} (see {@code lib/optimizeHeatmap.ts}) so the look
 * matches Optimize exactly. Toggle between execution count, average duration and p90 duration; the
 * heat recolors live and hovering a node shows its full metrics. Models without diagram layout are
 * auto-laid-out on the fly.
 */
export function ProcessHeatmap({
  process,
  elements,
}: {
  process: string;
  elements: ElementDuration[];
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const viewerRef = useRef<any>(null);
  const heatRef = useRef<SVGImageElement | null>(null);
  const [ready, setReady] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [metric, setMetric] = useState<Metric>("avg");

  // Create the viewer and import the diagram whenever the process changes.
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

  // (Re)render the heat overlay + hover tooltip when ready, or the data/metric change.
  useEffect(() => {
    const viewer = viewerRef.current;
    if (!viewer || !ready) {
      return;
    }
    const values: Record<string, number> = {};
    for (const e of elements) {
      values[e.elementId] = value(e, metric);
    }

    // Optimize's heatmap image, appended into the canvas viewport (pans + zooms with the diagram).
    // Rendered per active plane (getHeatmap only draws nodes on the current plane): re-render on
    // `root.set` so drilling into / out of a collapsed sub-process recomputes the heat for the
    // plane now shown, matching Optimize's drilldown behaviour.
    const canvas = viewer.get("canvas");
    const renderHeat = () => {
      const viewport = canvas._viewport as SVGGElement;
      if (heatRef.current && heatRef.current.parentNode) {
        heatRef.current.parentNode.removeChild(heatRef.current);
      }
      try {
        const node = getHeatmap(viewer, values);
        viewport.appendChild(node);
        heatRef.current = node;
      } catch (e) {
        // eslint-disable-next-line no-console
        console.error("heatmap render failed", e);
      }
    };
    renderHeat();

    // hover tooltip (Optimize-style): reveal the element's metrics on element.hover
    const eventBus = viewer.get("eventBus");
    const overlays = viewer.get("overlays");
    const byId = new Map(elements.map((e) => [e.elementId, e] as const));
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const onHover = (evt: any) => {
      const e = byId.get(evt.element?.id);
      if (!e) {
        return;
      }
      overlays.remove({ type: "heat-tooltip" });
      overlays.add(e.elementId, "heat-tooltip", {
        position: { bottom: 0, left: 0 },
        html: tooltipHtml(e),
      });
    };
    const onOut = () => overlays.remove({ type: "heat-tooltip" });
    eventBus.on("element.hover", onHover);
    eventBus.on("element.out", onOut);
    eventBus.on("root.set", renderHeat);

    return () => {
      eventBus.off("element.hover", onHover);
      eventBus.off("element.out", onOut);
      eventBus.off("root.set", renderHeat);
      try {
        overlays.remove({ type: "heat-tooltip" });
      } catch {
        // viewer gone
      }
    };
  }, [ready, elements, metric]);

  return (
    <Card>
      <CardHeader>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <CardTitle>Flow-node heatmap</CardTitle>
            <CardDescription>
              Executions and duration per element, on the process diagram
            </CardDescription>
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
        {error ? (
          <p className="py-8 text-center text-neutral-foreground-muted">{error}</p>
        ) : null}
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

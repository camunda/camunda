/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useMemo } from "react";
import type { JourneyActivity } from "../../lib/api";
import { chartColor } from "../../lib/chartColors";

/** Above this many distinct elements the layered layout stops being readable -- the caller falls
 * back to rendering the Timeline view instead (see {@link distinctElementCount}). */
export const MAX_MINI_MAP_NODES = 40;

const NODE_WIDTH = 150;
const NODE_HEIGHT = 32;
const COLUMN_GAP = 190;
const ROW_GAP = 54;

/** "Activity_ManualCreditReview" -> "Manual Credit Review", copied from ObjectDetailPage's own
 * helper (kept private there) since this is the one other place that needs it. */
function prettyElementName(elementId: string): string {
  const withoutPrefix = elementId.replace(
    /^(Activity|Event|Gateway|Flow|StartEvent|EndEvent|SubProcess|Task|ServiceTask|UserTask|CallActivity)_/i,
    "",
  );
  const spaced = withoutPrefix
    .replace(/[_-]+/g, " ")
    .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
    .trim();
  return spaced.length === 0 ? elementId : spaced.replace(/\b\w/g, (c) => c.toUpperCase());
}

/** Distinct elementIds across the whole journey -- the node count the map view would render. */
export function distinctElementCount(activities: JourneyActivity[]): number {
  return new Set(activities.map((a) => a.elementId)).size;
}

interface DfgNode {
  elementId: string;
  processId: string;
  layer: number;
}

interface DfgEdge {
  from: string;
  to: string;
  count: number;
}

/** Builds the directly-follows graph: per instance, sort activities by start time and count each
 * consecutive elementId pair once. */
function buildDfg(activities: JourneyActivity[]): { nodes: Map<string, DfgNode>; edges: DfgEdge[] } {
  const byInstance = new Map<number, JourneyActivity[]>();
  for (const a of activities) {
    const list = byInstance.get(a.instanceKey) ?? [];
    list.push(a);
    byInstance.set(a.instanceKey, list);
  }

  const nodes = new Map<string, DfgNode>();
  const edgeCounts = new Map<string, number>();
  for (const list of byInstance.values()) {
    const sorted = [...list].sort((x, y) => new Date(x.startedAt).getTime() - new Date(y.startedAt).getTime());
    for (const a of sorted) {
      if (!nodes.has(a.elementId)) {
        nodes.set(a.elementId, { elementId: a.elementId, processId: a.processId, layer: 0 });
      }
    }
    for (let i = 0; i < sorted.length - 1; i++) {
      const from = sorted[i].elementId;
      const to = sorted[i + 1].elementId;
      if (from === to) {
        continue; // no self-loop edges in the diagram
      }
      const edgeKey = `${from}->${to}`;
      edgeCounts.set(edgeKey, (edgeCounts.get(edgeKey) ?? 0) + 1);
    }
  }

  const edges = [...edgeCounts.entries()].map(([key, count]) => {
    const [from, to] = key.split("->");
    return { from, to, count };
  });

  assignLayers(nodes, edges);
  return { nodes, edges };
}

/**
 * Topological left-to-right layering. A directly-follows graph can have cycles (retry loops), so a
 * plain longest-path pass would never terminate -- this first finds a spanning DAG by DFS, treating
 * any edge back to a node still on the current DFS stack as a loop-back (rendered, but not used for
 * layering), then assigns each node the longest path over that DAG.
 */
function assignLayers(nodes: Map<string, DfgNode>, edges: DfgEdge[]): void {
  const adjacency = new Map<string, string[]>();
  for (const e of edges) {
    const list = adjacency.get(e.from) ?? [];
    list.push(e.to);
    adjacency.set(e.from, list);
  }

  const dagEdges: Array<[string, string]> = [];
  const state = new Map<string, "visiting" | "done">();
  const order: string[] = [];

  const visit = (id: string) => {
    state.set(id, "visiting");
    for (const next of adjacency.get(id) ?? []) {
      const nextState = state.get(next);
      if (nextState === "visiting") {
        continue; // back edge -- part of a loop, skip for layering purposes
      }
      dagEdges.push([id, next]);
      if (nextState !== "done") {
        visit(next);
      }
    }
    state.set(id, "done");
    order.push(id);
  };

  for (const id of [...nodes.keys()].sort()) {
    if (!state.has(id)) {
      visit(id);
    }
  }
  order.reverse(); // topological order of the retained DAG

  const layer = new Map<string, number>();
  for (const id of order) {
    layer.set(id, layer.get(id) ?? 0);
  }
  for (const id of order) {
    const from = layer.get(id) ?? 0;
    for (const [u, v] of dagEdges) {
      if (u === id) {
        layer.set(v, Math.max(layer.get(v) ?? 0, from + 1));
      }
    }
  }

  for (const [id, node] of nodes) {
    node.layer = layer.get(id) ?? 0;
  }
}

/**
 * A directly-follows-graph mini-map of one object's journey: every distinct BPMN element the
 * journey touched as a node, arrows for the transitions actually observed (labeled with a count
 * above 1), laid out left-to-right by topological layer. Node border color is the owning process's
 * stable palette color (same convention as chart series colors) so a call-activity hop into another
 * process reads at a glance. This is the DFG rendering Celonis/mpmX-style process mining tools use
 * for a journey overview; kept intentionally plain (straight/elbow arrows, no physics layout).
 */
export function JourneyMiniMap({ activities }: { activities: JourneyActivity[] }) {
  const { nodes, edges, processIds } = useMemo(() => {
    const { nodes: nodeMap, edges: edgeList } = buildDfg(activities);
    const ids = [...new Set([...nodeMap.values()].map((n) => n.processId))].sort();
    return { nodes: nodeMap, edges: edgeList, processIds: ids };
  }, [activities]);

  if (nodes.size === 0) {
    return <p className="text-sm text-neutral-foreground-muted">No activities recorded.</p>;
  }

  const layerCount = Math.max(...[...nodes.values()].map((n) => n.layer)) + 1;
  const rowsByLayer = new Map<number, DfgNode[]>();
  for (const node of [...nodes.values()].sort((a, b) => a.elementId.localeCompare(b.elementId))) {
    const list = rowsByLayer.get(node.layer) ?? [];
    list.push(node);
    rowsByLayer.set(node.layer, list);
  }
  const maxRows = Math.max(...[...rowsByLayer.values()].map((l) => l.length));

  const width = layerCount * COLUMN_GAP + NODE_WIDTH;
  const height = maxRows * ROW_GAP + NODE_HEIGHT;

  const positionByElement = new Map<string, { x: number; y: number }>();
  for (const [layerIndex, rowNodes] of rowsByLayer) {
    const x = layerIndex * COLUMN_GAP + NODE_WIDTH / 2;
    rowNodes.forEach((node, i) => {
      const y = ((i + 0.5) * height) / rowNodes.length;
      positionByElement.set(node.elementId, { x, y });
    });
  }

  const colorOf = (processId: string) => chartColor(processIds.indexOf(processId));

  return (
    <svg viewBox={`0 0 ${width} ${height}`} width="100%" style={{ maxHeight: 420 }} role="img" aria-label="Journey directly-follows map">
      <defs>
        <marker id="journey-map-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
          <path d="M0,0 L10,5 L0,10 z" style={{ fill: "var(--border)" }} />
        </marker>
      </defs>

      {edges.map((e) => {
        const a = positionByElement.get(e.from);
        const b = positionByElement.get(e.to);
        if (!a || !b) {
          return null;
        }
        const dx = b.x - a.x;
        const dy = b.y - a.y;
        const dist = Math.hypot(dx, dy) || 1;
        const ux = dx / dist;
        const uy = dy / dist;
        const x1 = a.x + ux * (NODE_WIDTH / 2);
        const y1 = a.y + uy * (NODE_HEIGHT / 2);
        const x2 = b.x - ux * (NODE_WIDTH / 2 + 6);
        const y2 = b.y - uy * (NODE_HEIGHT / 2 + 6);
        const midX = (x1 + x2) / 2;
        const midY = (y1 + y2) / 2;
        return (
          <g key={`${e.from}->${e.to}`}>
            <line x1={x1} y1={y1} x2={x2} y2={y2} style={{ stroke: "var(--border)" }} strokeWidth={1.5} markerEnd="url(#journey-map-arrow)" />
            {e.count > 1 && (
              <>
                <rect x={midX - 10} y={midY - 8} width={20} height={14} rx={3} style={{ fill: "var(--background)" }} />
                <text x={midX} y={midY + 3} textAnchor="middle" fontSize={10} style={{ fill: "var(--neutral-foreground-subtle)" }}>
                  {e.count}
                </text>
              </>
            )}
          </g>
        );
      })}

      {[...nodes.values()].map((node) => {
        const p = positionByElement.get(node.elementId);
        if (!p) {
          return null;
        }
        const label = prettyElementName(node.elementId);
        const displayLabel = label.length > 18 ? `${label.slice(0, 17)}…` : label;
        return (
          <g key={node.elementId}>
            <rect
              x={p.x - NODE_WIDTH / 2}
              y={p.y - NODE_HEIGHT / 2}
              width={NODE_WIDTH}
              height={NODE_HEIGHT}
              rx={8}
              style={{ fill: "var(--background)", stroke: colorOf(node.processId), strokeWidth: 2 }}
            />
            <text x={p.x} y={p.y + 4} textAnchor="middle" fontSize={11} style={{ fill: "var(--foreground)" }}>
              <title>{node.elementId}</title>
              {displayLabel}
            </text>
          </g>
        );
      })}
    </svg>
  );
}

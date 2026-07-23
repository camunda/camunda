/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router";
import { Card, CardContent, CardHeader, CardTitle } from "@camunda/design-system";
import { objectsGraphApi, type GraphEdge, type GraphNode } from "../../lib/objectsGraphApi";

const ROW_HEIGHT = 46;
const NODE_WIDTH = 150;
const NODE_HEIGHT = 30;
const COLUMN_GAP = 210;

function nodeKey(n: { type: string; id: string }): string {
  return `${n.type}:${n.id}`;
}

interface Positioned {
  node: GraphNode;
  x: number;
  y: number;
}

/** Evenly stacks `nodes` down a single vertical column at `x`, spread across `height`. */
function columnPositions(nodes: GraphNode[], x: number, height: number): Positioned[] {
  if (nodes.length === 0) {
    return [];
  }
  const step = height / nodes.length;
  return nodes.map((node, i) => ({ node, x, y: step * (i + 0.5) }));
}

/** "invoice" / "INV-42" -> "invoice INV-42" truncated for the fixed-width node rect. */
function nodeLabel(n: GraphNode): string {
  const label = `${n.type} ${n.id}`;
  return label.length > 20 ? `${label.slice(0, 19)}…` : label;
}

/**
 * One object's immediate neighborhood (depth 1): what contains it and what it contains, plus who
 * else shares an instance with it -- three columns (parents/co-sighted on the left, the object
 * itself centered, children on the right), solid arrows for CONTAINS and dashed labeled arrows for
 * CO_SIGHTED. Renders nothing (not even an empty frame) when the ego has no depth-1 neighbors --
 * the sibling "Related objects" card already covers that empty state.
 */
export function EgoGraphCard({ type, id }: { type: string; id: string }) {
  const navigate = useNavigate();
  const [data, setData] = useState<{ nodes: GraphNode[]; edges: GraphEdge[] } | null>(null);

  useEffect(() => {
    let cancelled = false;
    setData(null);
    objectsGraphApi.graph({ type, id, depth: 1 }).then((result) => {
      if (cancelled) {
        return;
      }
      if (result.ok) {
        setData({ nodes: result.data.nodes, edges: result.data.edges });
      }
    });
    return () => {
      cancelled = true;
    };
  }, [type, id]);

  const egoKey = nodeKey({ type, id });

  const layout = useMemo(() => {
    if (!data) {
      return null;
    }
    const leftMap = new Map<string, GraphNode>();
    const rightMap = new Map<string, GraphNode>();
    const parentEdges: GraphEdge[] = [];
    const childEdges: GraphEdge[] = [];
    const coSightedEdges: GraphEdge[] = [];

    for (const e of data.edges) {
      const sourceKey = `${e.sourceType}:${e.sourceId}`;
      const targetKey = `${e.targetType}:${e.targetId}`;
      if (e.kind === "CONTAINS") {
        if (targetKey === egoKey && sourceKey !== egoKey) {
          leftMap.set(sourceKey, { type: e.sourceType, id: e.sourceId });
          parentEdges.push(e);
        } else if (sourceKey === egoKey && targetKey !== egoKey) {
          rightMap.set(targetKey, { type: e.targetType, id: e.targetId });
          childEdges.push(e);
        }
      } else {
        const other = sourceKey === egoKey ? { type: e.targetType, id: e.targetId } : { type: e.sourceType, id: e.sourceId };
        leftMap.set(nodeKey(other), other);
        coSightedEdges.push(e);
      }
    }

    const left = [...leftMap.values()];
    const right = [...rightMap.values()];
    if (left.length === 0 && right.length === 0) {
      return null;
    }

    const height = Math.max(left.length, right.length, 1) * ROW_HEIGHT;
    const width = COLUMN_GAP * 2 + NODE_WIDTH;
    const leftX = NODE_WIDTH / 2 + 4;
    const centerX = width / 2;
    const rightX = width - NODE_WIDTH / 2 - 4;
    const midY = height / 2;

    const leftPositions = columnPositions(left, leftX, height);
    const rightPositions = columnPositions(right, rightX, height);
    const positionByKey = new Map<string, Positioned>();
    for (const p of [...leftPositions, ...rightPositions]) {
      positionByKey.set(nodeKey(p.node), p);
    }

    return { width, height, midY, centerX, leftPositions, rightPositions, positionByKey, parentEdges, childEdges, coSightedEdges };
  }, [data, egoKey]);

  if (!layout) {
    return null;
  }

  const { width, height, midY, centerX, leftPositions, rightPositions, positionByKey, parentEdges, childEdges, coSightedEdges } = layout;

  const goToNode = (n: GraphNode) => navigate(`/objects/${encodeURIComponent(n.type)}/${encodeURIComponent(n.id)}`);

  return (
    <Card>
      <CardHeader>
        <CardTitle>Object graph</CardTitle>
      </CardHeader>
      <CardContent>
        <svg viewBox={`0 0 ${width} ${height}`} width="100%" style={{ maxHeight: 360 }} role="img" aria-label="Related objects graph">
          <defs>
            <marker id="ego-graph-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
              <path d="M0,0 L10,5 L0,10 z" style={{ fill: "var(--border)" }} />
            </marker>
          </defs>

          {parentEdges.map((e) => {
            const p = positionByKey.get(`${e.sourceType}:${e.sourceId}`);
            if (!p) {
              return null;
            }
            return (
              <line
                key={`parent-${e.sourceType}-${e.sourceId}`}
                x1={p.x + NODE_WIDTH / 2}
                y1={p.y}
                x2={centerX - NODE_WIDTH / 2 - 8}
                y2={midY}
                style={{ stroke: "var(--border)" }}
                strokeWidth={1.5}
                markerEnd="url(#ego-graph-arrow)"
              />
            );
          })}

          {childEdges.map((e) => {
            const p = positionByKey.get(`${e.targetType}:${e.targetId}`);
            if (!p) {
              return null;
            }
            return (
              <line
                key={`child-${e.targetType}-${e.targetId}`}
                x1={centerX + NODE_WIDTH / 2 + 8}
                y1={midY}
                x2={p.x - NODE_WIDTH / 2}
                y2={p.y}
                style={{ stroke: "var(--border)" }}
                strokeWidth={1.5}
                markerEnd="url(#ego-graph-arrow)"
              />
            );
          })}

          {coSightedEdges.map((e) => {
            const sourceKey = `${e.sourceType}:${e.sourceId}`;
            const otherKey = sourceKey === egoKey ? `${e.targetType}:${e.targetId}` : sourceKey;
            const p = positionByKey.get(otherKey);
            if (!p) {
              return null;
            }
            const midX = (p.x + NODE_WIDTH / 2 + centerX - NODE_WIDTH / 2 - 8) / 2;
            const midLineY = (p.y + midY) / 2;
            return (
              <g key={`co-${otherKey}`}>
                <line
                  x1={p.x + NODE_WIDTH / 2}
                  y1={p.y}
                  x2={centerX - NODE_WIDTH / 2 - 8}
                  y2={midY}
                  style={{ stroke: "var(--border)" }}
                  strokeWidth={1.5}
                  strokeDasharray="5,4"
                />
                <text x={midX} y={midLineY - 4} textAnchor="middle" fontSize={10} style={{ fill: "var(--neutral-foreground-subtle)" }}>
                  {e.nInstances} instance{e.nInstances === 1 ? "" : "s"}
                </text>
              </g>
            );
          })}

          <g role="link" tabIndex={0} aria-hidden="true">
            <rect
              x={centerX - NODE_WIDTH / 2}
              y={midY - NODE_HEIGHT / 2}
              width={NODE_WIDTH}
              height={NODE_HEIGHT}
              rx={8}
              style={{ fill: "var(--background)", stroke: "var(--foreground)", strokeWidth: 2 }}
            />
            <text x={centerX} y={midY + 4} textAnchor="middle" fontSize={12} fontWeight={600} style={{ fill: "var(--foreground)" }}>
              <title>{`${type} ${id}`}</title>
              {nodeLabel({ type, id })}
            </text>
          </g>

          {[...leftPositions, ...rightPositions].map((p) => (
            <g
              key={nodeKey(p.node)}
              className="cursor-pointer"
              role="link"
              tabIndex={0}
              aria-label={`Open ${p.node.type} ${p.node.id}`}
              onClick={() => goToNode(p.node)}
              onKeyDown={(e) => {
                if (e.key === "Enter" || e.key === " ") {
                  e.preventDefault();
                  goToNode(p.node);
                }
              }}
            >
              <rect
                x={p.x - NODE_WIDTH / 2}
                y={p.y - NODE_HEIGHT / 2}
                width={NODE_WIDTH}
                height={NODE_HEIGHT}
                rx={8}
                style={{ fill: "var(--background)", stroke: "var(--border)", strokeWidth: 1.5 }}
              />
              <text x={p.x} y={p.y + 4} textAnchor="middle" fontSize={11} style={{ fill: "var(--foreground)" }}>
                <title>{`${p.node.type} ${p.node.id}`}</title>
                {nodeLabel(p.node)}
              </text>
            </g>
          ))}
        </svg>
      </CardContent>
    </Card>
  );
}

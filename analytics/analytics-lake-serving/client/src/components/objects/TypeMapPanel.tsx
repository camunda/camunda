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
import { objectsGraphApi, type TypeMapEdge } from "../../lib/objectsGraphApi";
import { chartColor } from "../../lib/chartColors";

const NODE_WIDTH = 132;
const NODE_HEIGHT = 34;
const RADIUS_PER_NODE = 42;
const MIN_RADIUS = 120;
const LABEL_HALF_WIDTH = 15;

interface Placed {
  type: string;
  x: number;
  y: number;
}

/** "notificationPreferenceCandidate" -> "notificationPrefe…" -- keeps the fixed-width node rect
 * readable; the full type name stays in the title tooltip. */
function truncateLabel(type: string): string {
  return type.length > 16 ? `${type.slice(0, 15)}…` : type;
}

/** Places every object type evenly around a circle -- deterministic regardless of how the
 * relation graph is shaped (a left-to-right topological layout would need a DAG, which
 * object_relations doesn't guarantee). Radius grows with the type count so labels don't collide. */
function layoutCircular(types: string[]): { placed: Placed[]; size: number } {
  const radius = Math.max(MIN_RADIUS, types.length * RADIUS_PER_NODE);
  const size = radius * 2 + NODE_WIDTH + 32;
  const cx = size / 2;
  const cy = size / 2;
  const placed = types.map((type, i) => {
    const angle = (2 * Math.PI * i) / types.length - Math.PI / 2;
    return { type, x: cx + radius * Math.cos(angle), y: cy + radius * Math.sin(angle) };
  });
  return { placed, size };
}

/** Shrinks a center-to-center line down to the two (approximate, circular) node edges, leaving
 * room for the arrowhead marker at the target end. */
function edgeEndpoints(a: Placed, b: Placed) {
  const dx = b.x - a.x;
  const dy = b.y - a.y;
  const dist = Math.hypot(dx, dy) || 1;
  const ux = dx / dist;
  const uy = dy / dist;
  const nodeRadius = Math.hypot(NODE_WIDTH, NODE_HEIGHT) / 2;
  return {
    x1: a.x + ux * nodeRadius,
    y1: a.y + uy * nodeRadius,
    x2: b.x - ux * (nodeRadius + 6),
    y2: b.y - uy * (nodeRadius + 6),
  };
}

/**
 * Type-level map of the object fabric: every object type discovered in `object_relations` as a
 * node, with labeled arrows showing how many instance-level edges connect each pair. Hand-rolled
 * SVG (no charting library) since this is a small relationship diagram, not a data chart. Renders
 * nothing at all -- no empty box -- when the backing view has no edges yet.
 */
export function TypeMapPanel() {
  const navigate = useNavigate();
  const [edges, setEdges] = useState<TypeMapEdge[] | null>(null);

  useEffect(() => {
    let cancelled = false;
    objectsGraphApi.typeMap().then((result) => {
      if (cancelled) {
        return;
      }
      setEdges(result.ok ? result.data.edges : []);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const types = useMemo(() => {
    if (!edges) {
      return [];
    }
    const set = new Set<string>();
    for (const e of edges) {
      set.add(e.parentType);
      set.add(e.childType);
    }
    return [...set].sort();
  }, [edges]);

  if (!edges || edges.length === 0 || types.length === 0) {
    return null;
  }

  const { placed, size } = layoutCircular(types);
  const byType = new Map(placed.map((p) => [p.type, p]));

  const goToType = (type: string) => navigate(`/objects/${encodeURIComponent(type)}`);

  return (
    <Card>
      <CardHeader>
        <CardTitle>Object type map</CardTitle>
      </CardHeader>
      <CardContent>
        <svg
          viewBox={`0 0 ${size} ${size}`}
          width="100%"
          style={{ maxHeight: 420 }}
          role="img"
          aria-label="Object relation map by type"
        >
          <defs>
            <marker
              id="type-map-arrow"
              viewBox="0 0 10 10"
              refX="9"
              refY="5"
              markerWidth="7"
              markerHeight="7"
              orient="auto-start-reverse"
            >
              <path d="M0,0 L10,5 L0,10 z" style={{ fill: "var(--border)" }} />
            </marker>
          </defs>

          {edges.map((e) => {
            const a = byType.get(e.parentType);
            const b = byType.get(e.childType);
            if (!a || !b || a.type === b.type) {
              return null;
            }
            const { x1, y1, x2, y2 } = edgeEndpoints(a, b);
            const midX = (x1 + x2) / 2;
            const midY = (y1 + y2) / 2;
            return (
              <g key={`${e.parentType}->${e.childType}`}>
                <line
                  x1={x1}
                  y1={y1}
                  x2={x2}
                  y2={y2}
                  style={{ stroke: "var(--border)" }}
                  strokeWidth={1.5}
                  markerEnd="url(#type-map-arrow)"
                />
                <rect
                  x={midX - LABEL_HALF_WIDTH}
                  y={midY - 9}
                  width={LABEL_HALF_WIDTH * 2}
                  height={16}
                  rx={4}
                  style={{ fill: "var(--background)" }}
                />
                <text
                  x={midX}
                  y={midY + 4}
                  textAnchor="middle"
                  fontSize={11}
                  style={{ fill: "var(--neutral-foreground-subtle)" }}
                >
                  {e.n}
                </text>
              </g>
            );
          })}

          {placed.map((p, i) => (
            <g
              key={p.type}
              className="cursor-pointer"
              role="link"
              tabIndex={0}
              aria-label={`Browse ${p.type} objects`}
              onClick={() => goToType(p.type)}
              onKeyDown={(e) => {
                if (e.key === "Enter" || e.key === " ") {
                  e.preventDefault();
                  goToType(p.type);
                }
              }}
            >
              <rect
                x={p.x - NODE_WIDTH / 2}
                y={p.y - NODE_HEIGHT / 2}
                width={NODE_WIDTH}
                height={NODE_HEIGHT}
                rx={8}
                style={{ fill: "var(--background)", stroke: chartColor(i), strokeWidth: 2 }}
              />
              <text
                x={p.x}
                y={p.y + 4}
                textAnchor="middle"
                fontSize={12}
                style={{ fill: "var(--foreground)" }}
              >
                <title>{p.type}</title>
                {truncateLabel(p.type)}
              </text>
            </g>
          ))}
        </svg>
      </CardContent>
    </Card>
  );
}

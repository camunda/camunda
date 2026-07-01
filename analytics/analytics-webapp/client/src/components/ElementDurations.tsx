/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import {
  Badge,
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@camunda/design-system";
import type { ElementDuration } from "../lib/api";
import { chartColor } from "../lib/chartColors";
import { formatCount, formatDuration } from "../lib/format";

/** Per-element (flow-node) duration table — the streaming form of Optimize's flownode heatmap. */
export function ElementDurations({ rows }: { rows: ElementDuration[] }) {
  const maxP90 = Math.max(1, ...rows.map((r) => r.p90Ms));
  return (
    <Card>
      <CardHeader>
        <CardTitle>Flow-node durations</CardTitle>
        <CardDescription>
          Per element: executions and duration (avg / p50 / p90 / max), heat by p90
        </CardDescription>
      </CardHeader>
      <CardContent>
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="border-b border-border text-left text-neutral-foreground-muted">
                <th className="py-2 pr-4 font-medium">Element</th>
                <th className="py-2 pr-4 font-medium">Type</th>
                <th className="py-2 pr-4 text-right font-medium">Executed</th>
                <th className="py-2 pr-4 text-right font-medium">Avg</th>
                <th className="py-2 pr-4 text-right font-medium">p50</th>
                <th className="py-2 pr-4 text-right font-medium">p90</th>
                <th className="py-2 pr-4 text-right font-medium">Max</th>
                <th className="w-40 py-2 font-medium">p90 heat</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.elementId} className="border-b border-border/60">
                  <td className="py-2 pr-4 font-medium">{r.elementId}</td>
                  <td className="py-2 pr-4">
                    <Badge variant="secondary">{r.elementType}</Badge>
                  </td>
                  <td className="py-2 pr-4 text-right tabular-nums">{formatCount(r.executedCount)}</td>
                  <td className="py-2 pr-4 text-right tabular-nums">{formatDuration(r.avgMs)}</td>
                  <td className="py-2 pr-4 text-right tabular-nums">{formatDuration(r.p50Ms)}</td>
                  <td className="py-2 pr-4 text-right tabular-nums">{formatDuration(r.p90Ms)}</td>
                  <td className="py-2 pr-4 text-right tabular-nums">{formatDuration(r.maxMs)}</td>
                  <td className="py-2">
                    <div className="h-2.5 w-full rounded bg-neutral-background-subtle">
                      <div
                        className="h-2.5 rounded"
                        style={{
                          width: `${Math.max(4, (r.p90Ms / maxP90) * 100)}%`,
                          backgroundColor: chartColor(1),
                        }}
                      />
                    </div>
                  </td>
                </tr>
              ))}
              {rows.length === 0 ? (
                <tr>
                  <td colSpan={8} className="py-6 text-center text-neutral-foreground-muted">
                    No flow-node data for this process yet.
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </CardContent>
    </Card>
  );
}

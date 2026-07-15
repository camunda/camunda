/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@camunda/design-system";
import type { ReworkHotspot } from "../lib/api";
import { formatCount } from "../lib/format";

/**
 * Flow nodes executed more than once per instance (loops, retries, send-backs), sorted by the
 * rework estimate. Activations are exact; distinct instances come from a sketch, so the rework
 * figure is exact for small counts and an approximation at scale.
 */
export function ReworkHotspots({ rows }: { rows: ReworkHotspot[] }) {
  const maxRework = Math.max(1, ...rows.map((r) => r.rework));
  return (
    <Card>
      <CardHeader>
        <CardTitle>Rework hotspots</CardTitle>
        <CardDescription>
          Repeated executions per flow node (activations − distinct instances); estimated at scale
        </CardDescription>
      </CardHeader>
      <CardContent>
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="border-b border-border text-left text-neutral-foreground-muted">
                <th className="py-2 pr-4 font-medium">Flow node</th>
                <th className="py-2 pr-4 text-right font-medium">Activations</th>
                <th className="py-2 pr-4 text-right font-medium">Instances</th>
                <th className="w-1/3 py-2 pr-4 font-medium">Rework</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.elementId} className="border-b border-border/60">
                  <td className="py-2 pr-4 font-medium">{r.elementId}</td>
                  <td className="py-2 pr-4 text-right tabular-nums">
                    {formatCount(r.activations)}
                  </td>
                  <td className="py-2 pr-4 text-right tabular-nums">{formatCount(r.instances)}</td>
                  <td className="py-2 pr-4">
                    <div className="flex items-center gap-2">
                      <span className="w-10 text-right tabular-nums">{formatCount(r.rework)}</span>
                      <div className="h-2.5 flex-1 rounded bg-neutral-background-subtle">
                        <div
                          className="h-2.5 rounded"
                          style={{
                            width: `${Math.max(4, (r.rework / maxRework) * 100)}%`,
                            backgroundColor: "#f1c21b",
                          }}
                        />
                      </div>
                    </div>
                  </td>
                </tr>
              ))}
              {rows.length === 0 ? (
                <tr>
                  <td colSpan={4} className="py-6 text-center text-neutral-foreground-muted">
                    No rework detected for this process in range.
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

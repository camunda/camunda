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
import type { IncidentFlowNode } from "../lib/api";
import { formatCount } from "../lib/format";

/**
 * Incidents per flow node — the streaming form of Optimize's incident-frequency report. "Raised"
 * counts incidents created in the selected range (incidents, not instances — an instance can raise
 * several); "Open" is the current created−resolved gauge (range-independent).
 */
export function Incidents({ rows }: { rows: IncidentFlowNode[] }) {
  const maxRaised = Math.max(1, ...rows.map((r) => r.raised));
  return (
    <Card>
      <CardHeader>
        <CardTitle>Incidents by flow node</CardTitle>
        <CardDescription>
          Counts incidents (not instances): "raised" over the selected range, "open" right now
        </CardDescription>
      </CardHeader>
      <CardContent>
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="border-b border-border text-left text-neutral-foreground-muted">
                <th className="py-2 pr-4 font-medium">Flow node</th>
                <th className="w-1/2 py-2 pr-4 font-medium">Raised (in range)</th>
                <th className="py-2 pr-4 text-right font-medium">Open (now)</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.elementId} className="border-b border-border/60">
                  <td className="py-2 pr-4 font-medium">{r.elementId}</td>
                  <td className="py-2 pr-4">
                    {/* count + proportional bar together, so the single "Raised" reads clearly */}
                    <div className="flex items-center gap-2">
                      <span className="w-10 text-right tabular-nums">{formatCount(r.raised)}</span>
                      <div className="h-2.5 flex-1 rounded bg-neutral-background-subtle">
                        <div
                          className="h-2.5 rounded"
                          style={{
                            width: `${Math.max(4, (r.raised / maxRaised) * 100)}%`,
                            backgroundColor: "#d1493b",
                          }}
                        />
                      </div>
                    </div>
                  </td>
                  <td className="py-2 pr-4 text-right tabular-nums">
                    {r.open > 0 ? (
                      <span style={{ color: "#d1493b", fontWeight: 600 }}>{formatCount(r.open)}</span>
                    ) : (
                      formatCount(r.open)
                    )}
                  </td>
                </tr>
              ))}
              {rows.length === 0 ? (
                <tr>
                  <td colSpan={3} className="py-6 text-center text-neutral-foreground-muted">
                    No incidents for this process in range.
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

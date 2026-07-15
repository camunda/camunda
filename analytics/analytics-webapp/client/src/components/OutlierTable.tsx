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
import type { ElementOutlier } from "../lib/api";
import { formatCount, formatDuration, formatPercent } from "../lib/format";

/**
 * Per-flow-node duration outliers: the boxplot fence (Q3 + 1.5*IQR) computed from each node's
 * completion-duration sketch, and how many/what share of its completions sit above it. Sorted by
 * outlier count, with a bar relative to the busiest node's outlier count.
 */
export function OutlierTable({ rows }: { rows: ElementOutlier[] }) {
  const maxCount = Math.max(1, ...rows.map((r) => r.count));
  return (
    <Card>
      <CardHeader>
        <CardTitle>Duration outliers</CardTitle>
        <CardDescription>
          Flow nodes with completions above the boxplot fence (Q3 + 1.5×IQR), approximate,
          sketch-based
        </CardDescription>
      </CardHeader>
      <CardContent>
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="border-b border-border text-left text-neutral-foreground-muted">
                <th className="py-2 pr-4 font-medium">Flow node</th>
                <th className="py-2 pr-4 text-right font-medium">n</th>
                <th className="py-2 pr-4 text-right font-medium">Median</th>
                <th className="py-2 pr-4 text-right font-medium">Fence</th>
                <th className="w-1/3 py-2 pr-4 font-medium">Outliers</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.elementId} className="border-b border-border/60">
                  <td className="py-2 pr-4 font-medium">{r.elementId}</td>
                  <td className="py-2 pr-4 text-right tabular-nums">{formatCount(r.n)}</td>
                  <td className="py-2 pr-4 text-right tabular-nums">
                    {formatDuration(r.medianMs)}
                  </td>
                  <td className="py-2 pr-4 text-right tabular-nums">{formatDuration(r.fenceMs)}</td>
                  <td className="py-2 pr-4">
                    <div className="flex items-center gap-2">
                      <div className="h-2.5 flex-1 rounded bg-neutral-background-subtle">
                        <div
                          className="h-2.5 rounded"
                          style={{
                            width: `${Math.max(4, (r.count / maxCount) * 100)}%`,
                            backgroundColor: "#da1e28",
                          }}
                        />
                      </div>
                      <span className="w-28 text-right text-xs tabular-nums text-neutral-foreground-muted">
                        {formatCount(r.count)} · {formatPercent(r.share)}
                      </span>
                    </div>
                  </td>
                </tr>
              ))}
              {rows.length === 0 ? (
                <tr>
                  <td colSpan={5} className="py-6 text-center text-neutral-foreground-muted">
                    No outliers detected in range (approximate, sketch-based).
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

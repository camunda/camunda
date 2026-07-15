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
import type { VariableCorrelation } from "../lib/api";
import { formatCount, formatPercent } from "../lib/format";

/**
 * Which declared variable values are over-represented among duration outliers: for each
 * `variable=value`, how many instances carried it, what share of them sit above the process's
 * overall outlier fence, and the lift over the overall outlier share (`×1.8` means 1.8x as likely
 * to be an outlier as the average completion). Declared variables only ({@code corr-*} cubes).
 */
export function OutlierCorrelation({ rows }: { rows: VariableCorrelation[] }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Outlier correlation</CardTitle>
        <CardDescription>
          Which variable values are over-represented among duration outliers (approximate,
          sketch-based; declared variables only)
        </CardDescription>
      </CardHeader>
      <CardContent>
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="border-b border-border text-left text-neutral-foreground-muted">
                <th className="py-2 pr-4 font-medium">Variable = value</th>
                <th className="py-2 pr-4 text-right font-medium">Instances</th>
                <th className="py-2 pr-4 text-right font-medium">% above fence</th>
                <th className="py-2 text-right font-medium">Lift</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r, i) => (
                <tr key={`${i}-${r.variable}=${r.value}`} className="border-b border-border/60">
                  <td className="py-2 pr-4 font-mono text-xs">
                    {r.variable}={r.value}
                  </td>
                  <td className="py-2 pr-4 text-right tabular-nums">{formatCount(r.n)}</td>
                  <td className="py-2 pr-4 text-right tabular-nums">{formatPercent(r.share)}</td>
                  <td className="py-2 text-right tabular-nums font-medium">
                    ×{r.lift.toFixed(1)}
                  </td>
                </tr>
              ))}
              {rows.length === 0 ? (
                <tr>
                  <td colSpan={4} className="py-6 text-center text-neutral-foreground-muted">
                    No correlation data — no corr-* cube or no outliers in range.
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

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
import type { VariantCorrelation, VariantRow } from "../lib/api";
import { chartColor } from "../lib/chartColors";
import { formatCount, formatDuration, formatPercent } from "../lib/format";

/** Middle-truncates a long element chain so its start and end stay readable. */
function truncateMiddle(text: string, max: number): string {
  if (text.length <= max) {
    return text;
  }
  const half = Math.floor((max - 3) / 2);
  return `${text.slice(0, half)} … ${text.slice(text.length - half)}`;
}

/** The strongest driver for a variantHash across every declared variable (max lift), or undefined
 * when the variant has no correlation row (no corr-variant-* cube, or it never carried a driver
 * variable) — the caller renders no chip then. */
function topDriver(
  correlations: VariantCorrelation[],
  variantHash: string,
): VariantCorrelation | undefined {
  return correlations
    .filter((c) => c.variantHash === variantHash)
    .reduce<VariantCorrelation | undefined>(
      (best, c) => (best === undefined || c.lift > best.lift ? c : best),
      undefined,
    );
}

/**
 * The top execution variants of the process, ranked by instance count: share bar, readable element
 * chain (order-insensitive canonical list — sorted element ids with loop-bucket markers, not the
 * execution order), the variant's duration percentiles for comparison, and (when a corr-variant-*
 * cube is provisioned) its strongest driver value as a chip. The driver column is additive and
 * optional — a missing correlation cube renders the table unchanged, without the chip.
 */
export function TopVariants({
  variants,
  correlations = [],
}: {
  variants: VariantRow[];
  correlations?: VariantCorrelation[];
}) {
  // Additive/optional column: with no correlation data at all (no corr-variant-* cube provisioned,
  // or nothing in range), the column disappears entirely rather than rendering an empty chip cell
  // on every row.
  const hasDrivers = correlations.length > 0;
  return (
    <Card>
      <CardHeader>
        <CardTitle>Top variants</CardTitle>
        <CardDescription>
          Distinct execution paths (element sets with loop buckets), ranked by instance count
        </CardDescription>
      </CardHeader>
      <CardContent>
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="border-b border-border text-left text-neutral-foreground-muted">
                <th className="py-2 pr-4 font-medium">#</th>
                <th className="w-1/4 py-2 pr-4 font-medium">Share</th>
                <th className="py-2 pr-4 font-medium">Elements</th>
                <th className="py-2 pr-4 text-right font-medium">Instances</th>
                <th className="py-2 pr-4 text-right font-medium">p50</th>
                <th className="py-2 text-right font-medium">p95</th>
                {hasDrivers ? <th className="py-2 pl-4 font-medium">Driver</th> : null}
              </tr>
            </thead>
            <tbody>
              {variants.map((v, i) => {
                const driver = hasDrivers ? topDriver(correlations, v.variantHash) : undefined;
                return (
                  <tr key={String(v.variantHash)} className="border-b border-border/60">
                    <td className="py-2 pr-4 tabular-nums text-neutral-foreground-muted">
                      {i + 1}
                    </td>
                    <td className="py-2 pr-4">
                      <div className="flex items-center gap-2">
                        <div className="h-2.5 flex-1 rounded bg-neutral-background-subtle">
                          <div
                            className="h-2.5 rounded"
                            style={{
                              width: `${Math.max(4, v.share * 100)}%`,
                              backgroundColor: chartColor(i),
                            }}
                          />
                        </div>
                        <span className="w-12 text-right text-xs tabular-nums">
                          {formatPercent(v.share)}
                        </span>
                      </div>
                    </td>
                    <td className="max-w-md py-2 pr-4">
                      <span className="font-mono text-xs" title={v.elements}>
                        {v.elements ? truncateMiddle(v.elements, 96) : "—"}
                      </span>
                    </td>
                    <td className="py-2 pr-4 text-right tabular-nums">{formatCount(v.count)}</td>
                    <td className="py-2 pr-4 text-right tabular-nums">
                      {formatDuration(v.p50Ms)}
                    </td>
                    <td className="py-2 text-right tabular-nums">{formatDuration(v.p95Ms)}</td>
                    {hasDrivers ? (
                      <td className="py-2 pl-4">
                        {driver ? (
                          <span className="inline-flex items-center rounded-full bg-neutral-background-subtle px-2 py-0.5 font-mono text-xs">
                            {driver.variable}={driver.value} ×{driver.lift.toFixed(1)}
                          </span>
                        ) : null}
                      </td>
                    ) : null}
                  </tr>
                );
              })}
              {variants.length === 0 ? (
                <tr>
                  <td
                    colSpan={hasDrivers ? 7 : 6}
                    className="py-6 text-center text-neutral-foreground-muted"
                  >
                    No ended instances with a variant signature in range.
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

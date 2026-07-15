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
import type { VariantRow } from "../lib/api";
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

/**
 * The top execution variants of the process, ranked by instance count: share bar, readable element
 * chain (order-insensitive canonical list — sorted element ids with loop-bucket markers, not the
 * execution order) and the variant's duration percentiles for comparison.
 */
export function TopVariants({ variants }: { variants: VariantRow[] }) {
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
              </tr>
            </thead>
            <tbody>
              {variants.map((v, i) => (
                <tr key={String(v.variantHash)} className="border-b border-border/60">
                  <td className="py-2 pr-4 tabular-nums text-neutral-foreground-muted">{i + 1}</td>
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
                  <td className="py-2 pr-4 text-right tabular-nums">{formatDuration(v.p50Ms)}</td>
                  <td className="py-2 text-right tabular-nums">{formatDuration(v.p95Ms)}</td>
                </tr>
              ))}
              {variants.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-6 text-center text-neutral-foreground-muted">
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

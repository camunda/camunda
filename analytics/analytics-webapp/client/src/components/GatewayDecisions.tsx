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
import type { BranchCorrelation, BranchDistribution } from "../lib/api";
import { chartColor } from "../lib/chartColors";
import { formatCount, formatPercent } from "../lib/format";

/**
 * How each exclusive gateway's traffic split over its outgoing branches: per gateway a compact
 * horizontal-bar breakdown of the branch targets, with counts and the activation-based share of
 * the gateway's traffic. Shares are activation-based, so a branch whose target is also reached
 * from elsewhere (e.g. a loop) can exceed 100%. When a {@code corr-branch-*} cube is provisioned,
 * each branch also shows its single strongest driver as a chip — additive and optional, so a
 * missing cube leaves the rows unchanged.
 */
export function GatewayDecisions({
  gateways,
  correlations = [],
}: {
  gateways: BranchDistribution[];
  correlations?: BranchCorrelation[];
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Gateway decisions</CardTitle>
        <CardDescription>
          Branch split per exclusive gateway (activation-based; loop targets can over-count)
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-5">
        {gateways.map((g) => (
          <div key={g.gatewayId} className="flex flex-col gap-2">
            <div className="flex items-baseline justify-between">
              <span className="text-sm font-medium">{g.gatewayLabel}</span>
              <span className="text-xs text-neutral-foreground-muted">
                {formatCount(g.activations)} decisions
              </span>
            </div>
            <div className="flex flex-col gap-1.5">
              {g.branches.map((b, i) => {
                const driver = correlations.find(
                  (c) => c.gatewayId === g.gatewayId && c.targetId === b.targetId,
                );
                return (
                  <div key={b.targetId} className="flex items-center gap-2 text-sm">
                    <span className="w-40 truncate" title={b.targetId}>
                      {b.targetLabel}
                    </span>
                    <div className="h-2.5 flex-1 rounded bg-neutral-background-subtle">
                      <div
                        className="h-2.5 rounded"
                        style={{
                          width: `${Math.min(100, Math.max(b.activations > 0 ? 4 : 0, b.share * 100))}%`,
                          backgroundColor: chartColor(i),
                        }}
                      />
                    </div>
                    <span className="w-24 text-right text-xs tabular-nums text-neutral-foreground-muted">
                      {formatCount(b.activations)} · {formatPercent(b.share)}
                    </span>
                    {driver ? (
                      <span className="inline-flex items-center rounded-full bg-neutral-background-subtle px-2 py-0.5 font-mono text-xs">
                        {driver.variable}={driver.value} ×{driver.lift.toFixed(1)}
                      </span>
                    ) : null}
                  </div>
                );
              })}
            </div>
          </div>
        ))}
        {gateways.length === 0 ? (
          <p className="py-4 text-center text-sm text-neutral-foreground-muted">
            No decision gateways in this process model.
          </p>
        ) : null}
      </CardContent>
    </Card>
  );
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { Card, CardContent } from "@camunda/design-system";
import type { ReactNode } from "react";

interface StatTileProps {
  label: string;
  value: string;
  hint?: ReactNode;
  accent?: string;
}

/** A single-number KPI tile (median, p99, SLA-met %, …) for the top row of the dashboard. */
export function StatTile({ label, value, hint, accent }: StatTileProps) {
  return (
    <Card className="h-full">
      <CardContent className="flex flex-col gap-1 py-4">
        <span className="text-xs font-medium uppercase tracking-wide text-neutral-foreground-muted">
          {label}
        </span>
        <span
          className="text-3xl font-semibold leading-tight tabular-nums"
          style={accent ? { color: accent } : undefined}
        >
          {value}
        </span>
        {hint ? (
          <span className="text-xs text-neutral-foreground-muted">{hint}</span>
        ) : null}
      </CardContent>
    </Card>
  );
}

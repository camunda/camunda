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
import type { OpenInstanceRow } from "../lib/api";
import { formatDuration } from "../lib/format";

const HOUR_MS = 3_600_000;

/**
 * Aging work in progress: the oldest currently-open instances (the open-instances working set —
 * inserted on activation, evicted on completion/termination), oldest first. Ages beyond an hour
 * are highlighted — those are the instances someone should look at.
 */
export function OpenInstancesTable({ rows }: { rows: OpenInstanceRow[] }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Aging work in progress</CardTitle>
        <CardDescription>Oldest running instances right now, by age</CardDescription>
      </CardHeader>
      <CardContent>
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="border-b border-border text-left text-neutral-foreground-muted">
                <th className="py-2 pr-4 font-medium">Instance</th>
                <th className="py-2 pr-4 font-medium">Started</th>
                <th className="py-2 pr-4 text-right font-medium">Age</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.processInstanceKey} className="border-b border-border/60">
                  <td className="py-2 pr-4 font-medium tabular-nums">{r.processInstanceKey}</td>
                  <td className="py-2 pr-4">{new Date(r.startedAt).toLocaleString()}</td>
                  <td className="py-2 pr-4 text-right tabular-nums">
                    {r.ageMs >= HOUR_MS ? (
                      <span style={{ color: "#d1493b", fontWeight: 600 }}>
                        {formatDuration(r.ageMs)}
                      </span>
                    ) : (
                      formatDuration(r.ageMs)
                    )}
                  </td>
                </tr>
              ))}
              {rows.length === 0 ? (
                <tr>
                  <td colSpan={3} className="py-6 text-center text-neutral-foreground-muted">
                    No open instances for this process.
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

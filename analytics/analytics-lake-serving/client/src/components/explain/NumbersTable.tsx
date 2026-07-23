/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@camunda/design-system";
import { formatCount } from "../../lib/format";

function renderValue(v: unknown): string {
  if (v == null) {
    return "–";
  }
  if (typeof v === "number") {
    return Number.isInteger(v) ? formatCount(v) : v.toFixed(3);
  }
  if (typeof v === "object") {
    return JSON.stringify(v);
  }
  return String(v);
}

/** The "numbers" behind a finding's claim sentence -- a flat key/value dump of whatever the
 * `numbers` bag actually contains (its exact shape is finding-kind-dependent and unspecified by the
 * contract), shown behind a "show numbers" toggle rather than always-on. */
export function NumbersTable({ numbers }: { numbers: Record<string, unknown> }) {
  const entries = Object.entries(numbers);
  if (entries.length === 0) {
    return <p className="text-sm text-neutral-foreground-muted">No numbers reported.</p>;
  }
  return (
    <Table size="sm">
      <TableHeader>
        <TableRow>
          <TableHead>Field</TableHead>
          <TableHead>Value</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {entries.map(([k, v]) => (
          <TableRow key={k}>
            <TableCell className="font-mono text-xs">{k}</TableCell>
            <TableCell className="tabular-nums">{renderValue(v)}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

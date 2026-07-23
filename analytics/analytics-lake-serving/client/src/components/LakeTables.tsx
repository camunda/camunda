/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@camunda/design-system";
import type { TableInfo } from "../lib/api";

/** The discovered lake views and their row counts -- GET /api/tables rendered as a table. */
export function LakeTables({ tables }: { tables: TableInfo[] }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Discovered tables</CardTitle>
        <CardDescription>
          Views registered at startup from directories under {"<warehouse>/lake/"}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="border-b border-border text-left text-neutral-foreground-muted">
                <th className="py-2 pr-4 font-medium">Table</th>
                <th className="py-2 pr-4 text-right font-medium">Row count</th>
              </tr>
            </thead>
            <tbody>
              {tables.map((t) => (
                <tr key={t.name} className="border-b border-border/60">
                  <td className="py-2 pr-4 font-medium">{t.name}</td>
                  <td className="py-2 pr-4 text-right tabular-nums">
                    {t.rowCount === null ? (
                      <span className="text-neutral-foreground-muted">unavailable</span>
                    ) : (
                      t.rowCount.toLocaleString()
                    )}
                  </td>
                </tr>
              ))}
              {tables.length === 0 ? (
                <tr>
                  <td colSpan={2} className="py-6 text-center text-neutral-foreground-muted">
                    No tables discovered yet -- run the lake writer against this warehouse first.
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

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useEffect, useState } from "react";
import { LakeTables } from "./components/LakeTables";
import { api, type TableInfo } from "./lib/api";

/**
 * Proof-of-life "Lake" screen: lists the tables {@code LakeViewRegistry} discovered at startup.
 * The explain-shaped screens (follow-up lane J) render alongside this, not in place of it.
 */
export default function App() {
  const [tables, setTables] = useState<TableInfo[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .tables()
      .then(setTables)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : String(e)));
  }, []);

  return (
    <div className="min-h-full bg-neutral-background-subtle text-neutral-foreground">
      <header className="border-b border-border bg-neutral-background">
        <div className="mx-auto flex max-w-4xl flex-col gap-1 px-6 py-4">
          <span className="text-lg font-semibold">Camunda · Analytics Lake</span>
          <span className="text-xs text-neutral-foreground-muted">
            Read-only serving over the lake warehouse
          </span>
        </div>
      </header>

      <main className="mx-auto max-w-4xl px-6 py-6">
        {error ? (
          <p className="text-destructive-foreground">Failed to load tables: {error}</p>
        ) : (
          <LakeTables tables={tables} />
        )}
      </main>
    </div>
  );
}

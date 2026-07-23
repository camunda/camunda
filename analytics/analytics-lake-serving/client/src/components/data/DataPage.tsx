/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useEffect, useState } from "react";
import { LakeTables } from "../LakeTables";
import { api, type TableInfo } from "../../lib/api";

/**
 * The Data tab: the proof-of-life "discovered tables" screen (formerly the whole app's root page)
 * now lives here alongside the dashboard/explain/objects screens.
 */
export function DataPage() {
  const [tables, setTables] = useState<TableInfo[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .tables()
      .then(setTables)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : String(e)));
  }, []);

  return error ? (
    <p className="text-destructive-foreground">Failed to load tables: {error}</p>
  ) : (
    <LakeTables tables={tables} />
  );
}

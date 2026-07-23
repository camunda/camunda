/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// Shapes mirror the Java records LakeController returns (Jackson serializes record components by
// name) -- see io.camunda.analytics.lake.serving.web.LakeController.

/** One discovered lake table: its view name and current row count (null if unavailable). */
export interface TableInfo {
  name: string;
  rowCount: number | null;
}

/** GET /api/query response body. */
export interface QueryResponse {
  columns: string[];
  rows: unknown[][];
}

async function getJson<T>(url: string): Promise<T> {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`${url} → HTTP ${response.status}`);
  }
  return response.json() as Promise<T>;
}

export const api = {
  health: () => getJson<{ status: string }>("/api/health"),
  tables: () => getJson<TableInfo[]>("/api/tables"),
  /** Plain SQL in, JSON columns/rows out -- same POST-plain-SQL convention analytics-lake's demo
   * UI already uses. */
  query: async (sql: string): Promise<QueryResponse> => {
    const response = await fetch("/api/query", {
      method: "POST",
      headers: { "Content-Type": "text/plain" },
      body: sql,
    });
    const body = (await response.json()) as QueryResponse | { error: string };
    if (!response.ok || "error" in body) {
      throw new Error("error" in body ? body.error : `HTTP ${response.status}`);
    }
    return body;
  },
};

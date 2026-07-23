/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// Client for POST /api/objects/stats plus the sort-aware POST /api/objects/list variant this
// lane's backend piece added (an optional `sort` field on ObjectListQuery). Kept as a sibling of
// lib/api.ts rather than folded into it (out of this lane's territory), so this file re-declares
// the small "never throws, always ok/error" request helper api.ts keeps private to itself instead
// of importing it.

import type { ApiResult, ObjectListResponse, ObjectStatus } from "./api";

/** One process's distinct-object-of-this-type count -- mirrors the backend's ByProcessRow. */
export interface ByProcessRow {
  processId: string;
  n: number;
}

/** One point of the children-per-object distribution ({@code children} = k) -- mirrors the
 * backend's RelationFanoutRow. */
export interface RelationFanoutRow {
  children: number;
  n: number;
}

/** One closing-outcome's count -- mirrors the backend's OutcomeRow. */
export interface OutcomeRow {
  outcome: string;
  n: number;
}

export interface ObjectsStatsRequest {
  type: string;
}

export interface ObjectsStatsResponse {
  byProcess: ByProcessRow[];
  relationFanout: RelationFanoutRow[];
  outcomes: OutcomeRow[];
}

export type ObjectSort = "FIRST_SEEN_DESC" | "DURATION_DESC";

/** Same shape as api.ts's ObjectListRequest, plus the additive `sort` field the backend lane
 * added to ObjectListQuery (not yet reflected in lib/api.ts's ObjectListRequest, out of scope for
 * this lane to touch). */
export interface ObjectListWithSortRequest {
  type: string;
  status: ObjectStatus;
  limit: number;
  offset: number;
  sort?: ObjectSort;
}

async function tryExtractErrorMessage(response: Response): Promise<string | null> {
  try {
    const body: unknown = await response.json();
    if (body && typeof body === "object" && "error" in body) {
      const error = (body as { error: unknown }).error;
      return typeof error === "string" ? error : JSON.stringify(error);
    }
  } catch {
    // response body wasn't JSON (or was empty) -- fall through to the plain HTTP-status message
  }
  return null;
}

async function request<T>(path: string, body: unknown): Promise<ApiResult<T>> {
  try {
    const response = await fetch(path, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    if (!response.ok) {
      const message = (await tryExtractErrorMessage(response)) ?? `HTTP ${response.status}`;
      return { ok: false, status: response.status, message };
    }
    const data = (await response.json()) as T;
    return { ok: true, data };
  } catch (e: unknown) {
    return { ok: false, status: null, message: e instanceof Error ? e.message : String(e) };
  }
}

export const objectsStatsApi = {
  stats: (req: ObjectsStatsRequest) => request<ObjectsStatsResponse>("/api/objects/stats", req),
  listSorted: (req: ObjectListWithSortRequest) =>
    request<ObjectListResponse>("/api/objects/list", req),
};

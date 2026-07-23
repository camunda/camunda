/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Shapes mirror io.camunda.analytics.lake.serving.tools.{GaugeSeriesQuery,GaugeSeriesService,
 * CohortShareQuery,CohortShareService} exactly (Jackson serializes record components by name) --
 * see web/GaugeController for the two endpoints this client calls. Kept as its own module (same
 * convention as lib/definitionsApi.ts/lib/objectsGraphApi.ts) rather than folded into lib/api.ts,
 * since neither endpoint shares ToolsController's per-entity request/response plumbing.
 *
 * These replace the earlier client-side-SQL approach (a lib/lakeSqlApi.ts that built raw
 * `POST /api/query` strings for the WIP tile and the Cohort survival tile) per an explicit
 * amendment: free-form SQL from the client is an anti-pattern reserved for the Data page alone: see
 * these typed endpoints instead.
 */
import type { Filters, SeriesPoint } from "./api";

export type ApiResult<T> = { ok: true; data: T } | { ok: false; status: number | null; message: string };

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

async function post<T>(path: string, body: unknown): Promise<ApiResult<T>> {
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

// ---------------------------------------------------------------------------------------------
// POST /api/tools/gauge-series
// ---------------------------------------------------------------------------------------------

export interface GaugeSeriesRequest {
  /** `null` asks for every process, summed per sample instant then bucket-averaged (see
   * GaugeSeriesService's own javadoc). */
  processId: string | null;
  from: number;
  to: number;
  grainMinutes: number;
}

export interface GaugeSeriesResponse {
  points: SeriesPoint[];
}

// ---------------------------------------------------------------------------------------------
// POST /api/tools/cohort-share
// ---------------------------------------------------------------------------------------------

export interface CohortShareRequest {
  entity: string;
  /** One share series per threshold, in the entity's measure's own unit (milliseconds for a
   * duration_ms measure). */
  thresholdsMs: number[];
  filters: Filters;
  from: number;
  to: number;
  grainMinutes: number;
}

export interface SharePoint {
  t: string;
  /** `null` when the bucket has zero histogram mass, not `0`/`NaN`. */
  share: number | null;
}

export interface ShareSeries {
  thresholdMs: number;
  points: SharePoint[];
}

export interface CohortShareResponse {
  series: ShareSeries[];
}

export const gaugeApi = {
  gaugeSeries: (req: GaugeSeriesRequest) => post<GaugeSeriesResponse>("/api/tools/gauge-series", req),
  cohortShare: (req: CohortShareRequest) => post<CohortShareResponse>("/api/tools/cohort-share", req),
};

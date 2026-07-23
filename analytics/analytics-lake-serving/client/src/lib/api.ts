/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// Shapes mirror the Java records LakeController / the investigate-tool controllers return
// (Jackson serializes record components by name) -- see
// io.camunda.analytics.lake.serving.web.LakeController and the sibling tool/investigate/objects
// controllers this client codes against by contract (the backend lane builds those in parallel;
// see the module report for exactly which fields are still ambiguous in the written contract).

// ---------------------------------------------------------------------------------------------
// Existing proof-of-life surface (unchanged) -- GET /api/tables, POST /api/query.
// ---------------------------------------------------------------------------------------------

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

// ---------------------------------------------------------------------------------------------
// Shared contract shapes.
// ---------------------------------------------------------------------------------------------

/** Row-level filter bag. The contract names this field on nearly every tool request without
 * specifying its shape; treated as an opaque object of field -> value equality filters (the
 * simplest reading), passed through untouched. Forms that build filters (Explain's entry form)
 * only ever produce this shape, so the ambiguity is confined to this one type. */
export type Filters = Record<string, string | number | boolean>;

export interface TimeWindow {
  from: number;
  to: number;
}

export type ChangepointShape = "STEP" | "DRIFT" | "NONE";

export type CohortSpec =
  | { type: "THRESHOLD"; measure: string; op: string; value: number }
  | { type: "WINDOW_SPLIT"; at: number };

export const TOOL_NAMES = [
  "series",
  "changepoint",
  "decompose",
  "screen",
  "cohort-compare",
  "exemplars",
  "conditions",
] as const;
export type ToolName = (typeof TOOL_NAMES)[number];

// ---------------------------------------------------------------------------------------------
// POST /api/tools/series
// ---------------------------------------------------------------------------------------------

export interface SeriesRequest {
  entity: string;
  measure: string | null;
  quantile: number | null;
  filters: Filters;
  from: number;
  to: number;
  grainMinutes: number;
}

export interface SeriesPoint {
  t: number;
  value: number;
}

export interface SeriesResponse {
  points: SeriesPoint[];
  sql: string;
  params: unknown;
}

// ---------------------------------------------------------------------------------------------
// POST /api/tools/changepoint  (same request shape as series, per the contract)
// ---------------------------------------------------------------------------------------------

export interface ChangepointResponse {
  at: number;
  shape: ChangepointShape;
  confidence: number;
  before: number;
  after: number;
  sql?: string;
  params?: unknown;
}

// ---------------------------------------------------------------------------------------------
// POST /api/tools/decompose
// ---------------------------------------------------------------------------------------------

/** "measure|quantile" in the contract is read as mirroring {@link SeriesRequest}'s split fields --
 * both present, one of the two null depending on which the caller wants decomposed. */
export interface DecomposeRequest {
  entity: string;
  measure: string | null;
  quantile: number | null;
  window: TimeWindow;
  baseline: TimeWindow;
  dim: string;
}

export interface DecomposeRow {
  value: string;
  current: number;
  baseline: number;
  delta: number;
  contributionShare: number;
}

export interface DecomposeResponse {
  rows: DecomposeRow[];
}

// ---------------------------------------------------------------------------------------------
// POST /api/tools/screen
// ---------------------------------------------------------------------------------------------

/** "targetSeries" is read as a full {@link SeriesRequest} (the contract doesn't spell out its
 * fields, but every other tool's series-shaped input takes this shape). "candidates" only ever
 * documents the literal "auto", so it is typed as that single literal rather than a wider enum. */
export interface ScreenRequest {
  targetSeries: SeriesRequest;
  window: TimeWindow;
  candidates: "auto";
}

export interface ScreenRow {
  series: string;
  shiftSlots: number;
  correlation: number;
  movedAt: number;
}

export interface ScreenResponse {
  rows: ScreenRow[];
}

// ---------------------------------------------------------------------------------------------
// POST /api/tools/cohort-compare
// ---------------------------------------------------------------------------------------------

export interface CohortCompareRequest {
  entity: string;
  cohort: CohortSpec;
  filters: Filters;
  from: number;
  to: number;
  attributes: "auto";
  supportFloor: number;
}

export interface CohortCompareRow {
  attribute: string;
  bucket: string;
  slowShare: number;
  fastShare: number;
  lift: number;
  slowN: number;
  fastN: number;
}

export interface CohortCompareResponse {
  rows: CohortCompareRow[];
}

// ---------------------------------------------------------------------------------------------
// POST /api/tools/exemplars
// ---------------------------------------------------------------------------------------------

export interface ExemplarsRequest {
  entity: string;
  cohort: CohortSpec;
  k: number;
}

export interface ExemplarRow {
  instanceKey: string;
  durationMs: number;
  startedAt: number;
  variantHash: string;
}

export interface ExemplarsResponse {
  rows: ExemplarRow[];
}

// ---------------------------------------------------------------------------------------------
// POST /api/tools/conditions
// ---------------------------------------------------------------------------------------------

export interface ConditionsRequest {
  variantHash: string;
  processId: string;
}

/** {elements, flows, firstSeen} isn't broken down further by the contract. Read as the variant's
 * BPMN footprint: the element/flow ids that make up the signature, and when it was first observed.
 * Rendered defensively (Array.isArray guards) so a different actual shape degrades to "no data"
 * rather than a crash. */
export interface ConditionsResponse {
  elements: unknown[];
  flows: unknown[];
  firstSeen: number | null;
}

// ---------------------------------------------------------------------------------------------
// POST /api/investigate
// ---------------------------------------------------------------------------------------------

export interface InvestigateRequest {
  entity: string;
  measure: string | null;
  quantile: number | null;
  filters: Filters;
  from: number;
  to: number;
}

/**
 * One ranked finding. `claim` and `numbers` are structured, kind-dependent bags -- the contract
 * doesn't pin down their shapes per kind, so both are read as opaque records and the phrasing
 * templates (lib/findingText.ts) pull fields out defensively, falling back to a generic sentence
 * for any kind they don't recognize. `kind` is a free-form string; only "SCAN_DEFERRED" is named
 * explicitly by the contract as a distinct rendering (estimated rows + "run scan" button) -- every
 * other kind string is this client's own invention pending the backend's actual taxonomy.
 */
export interface Finding {
  id: string;
  rung: number;
  kind: string;
  claim: Record<string, unknown>;
  numbers: Record<string, unknown>;
  support?: unknown;
  effect?: unknown;
  tool: ToolName;
  toolParams: Record<string, unknown>;
  sql: string;
}

export interface InvestigateResponse {
  spec: Record<string, unknown>;
  findings: Finding[];
}

// ---------------------------------------------------------------------------------------------
// GET /api/registry
// ---------------------------------------------------------------------------------------------

export interface EntityDim {
  name: string;
  kind: string;
}

export interface EntityDescriptor {
  name: string;
  dims: EntityDim[];
  measures: string[];
  hasHistogram: boolean;
}

export interface RegistryResponse {
  entities: EntityDescriptor[];
}

// ---------------------------------------------------------------------------------------------
// GET /api/objects/types, POST /api/objects/list, POST /api/objects/journey
// ---------------------------------------------------------------------------------------------

export interface ObjectTypesResponse {
  types: string[];
  closedSupported: boolean;
}

export type ObjectStatus = "OPEN" | "CLOSED" | "ALL";

export interface ObjectListRequest {
  type: string;
  status: ObjectStatus;
  limit: number;
  offset: number;
}

export interface ObjectRow {
  objectId: string;
  firstSeen: number;
  lastSeen: number;
  nInstances: number;
  closedAt: number | null;
  outcome: string | null;
  durationMs: number | null;
}

export interface ObjectListResponse {
  rows: ObjectRow[];
}

export interface ObjectJourneyRequest {
  type: string;
  id: string;
}

export interface JourneyActivity {
  instanceKey: string;
  processId: string;
  elementId: string;
  startedAt: number;
  endedAt: number | null;
  durationMs: number | null;
  attributedVia: "ROOT" | "SCOPE";
}

/** Best-effort read of "links"/"relations" -- the contract names them without a shape. Read as: a
 * relation = another object this one contains ("contained objects as links"); a link = a process
 * instance related to this object ("instance links panel"). Rendered defensively. */
export interface ObjectRelation {
  type?: string;
  objectId: string;
}

export interface ObjectInstanceLink {
  instanceKey: string;
  processId?: string;
}

export interface ObjectJourneyResponse {
  sightings: number;
  activities: JourneyActivity[];
  links: ObjectInstanceLink[];
  relations: ObjectRelation[];
}

// ---------------------------------------------------------------------------------------------
// Graceful request layer: never throws. Every caller gets an explicit ok/error union so a tile or
// page can render its own "not available yet" state instead of crashing on a 404/400/500 or a
// network failure (the backend lane may lag this client).
// ---------------------------------------------------------------------------------------------

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

async function request<T>(path: string, body?: unknown): Promise<ApiResult<T>> {
  try {
    const response = await fetch(
      path,
      body === undefined
        ? { method: "GET" }
        : { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) },
    );
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
  refresh: () => request<unknown>("/api/refresh", {}),

  registry: () => request<RegistryResponse>("/api/registry"),

  tools: {
    series: (req: SeriesRequest) => request<SeriesResponse>("/api/tools/series", req),
    changepoint: (req: SeriesRequest) => request<ChangepointResponse>("/api/tools/changepoint", req),
    decompose: (req: DecomposeRequest) => request<DecomposeResponse>("/api/tools/decompose", req),
    screen: (req: ScreenRequest) => request<ScreenResponse>("/api/tools/screen", req),
    cohortCompare: (req: CohortCompareRequest) =>
      request<CohortCompareResponse>("/api/tools/cohort-compare", req),
    exemplars: (req: ExemplarsRequest) => request<ExemplarsResponse>("/api/tools/exemplars", req),
    conditions: (req: ConditionsRequest) => request<ConditionsResponse>("/api/tools/conditions", req),
  },

  investigate: (req: InvestigateRequest) => request<InvestigateResponse>("/api/investigate", req),

  objects: {
    types: () => request<ObjectTypesResponse>("/api/objects/types"),
    list: (req: ObjectListRequest) => request<ObjectListResponse>("/api/objects/list", req),
    journey: (req: ObjectJourneyRequest) => request<ObjectJourneyResponse>("/api/objects/journey", req),
  },
};

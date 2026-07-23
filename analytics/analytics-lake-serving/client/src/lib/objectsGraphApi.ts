/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Shapes mirror io.camunda.analytics.lake.serving.objects.ObjectsGraphService's records exactly
 * (Jackson serializes record components by name) -- see ObjectsGraphController for the two
 * endpoints this client calls. Kept as its own module (rather than folded into lib/api.ts) since
 * it's a separately owned slice of the same /api/objects surface; the request/ApiResult plumbing
 * is duplicated in miniature rather than importing api.ts's private `request` helper (not
 * exported), matching the tolerance for a little duplication over widening that file's surface.
 */

// ---------------------------------------------------------------------------------------------
// GET /api/objects/type-map
// ---------------------------------------------------------------------------------------------

/** One (parentType, childType) pair from object_relations and how many edges realize it. */
export interface TypeMapEdge {
  parentType: string;
  childType: string;
  n: number;
}

export interface TypeMapResponse {
  edges: TypeMapEdge[];
}

// ---------------------------------------------------------------------------------------------
// POST /api/objects/graph
// ---------------------------------------------------------------------------------------------

export interface ObjectGraphRequest {
  type: string;
  id: string;
  depth?: 1 | 2;
}

export interface GraphNode {
  type: string;
  id: string;
}

export type GraphEdgeKind = "CONTAINS" | "CO_SIGHTED";

/** nInstances is only present (non-null) for CO_SIGHTED edges. */
export interface GraphEdge {
  sourceType: string;
  sourceId: string;
  targetType: string;
  targetId: string;
  kind: GraphEdgeKind;
  nInstances: number | null;
}

export interface ObjectGraphResponse {
  nodes: GraphNode[];
  edges: GraphEdge[];
  truncated: boolean;
}

// ---------------------------------------------------------------------------------------------
// Graceful request layer -- same ok/error union as lib/api.ts's ApiResult<T>, kept local so this
// module has no runtime dependency on api.ts (only a type-shape convention, not shared code).
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

export const objectsGraphApi = {
  typeMap: () => request<TypeMapResponse>("/api/objects/type-map"),
  graph: (req: ObjectGraphRequest) => request<ObjectGraphResponse>("/api/objects/graph", req),
};

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Shapes mirror io.camunda.analytics.lake.serving.objects.ProcessDefinitionsService's
 * ProcessDefinitionsListResult record exactly (Jackson serializes record components by name) --
 * see ProcessDefinitionsController's `GET /api/definitions/list`. Kept as its own module rather
 * than widening lib/definitionsApi.ts (owned by a sibling lane's Diagram-tab work) -- same
 * request/ApiResult duplication-over-import convention lib/objectsGraphApi.ts documents.
 */

// ---------------------------------------------------------------------------------------------
// GET /api/definitions/list
// ---------------------------------------------------------------------------------------------

export interface ProcessDefinitionVersion {
  version: number;
  deployedAt: string;
}

/** One process id's deployed versions, from the `process_definitions` dictionary table. */
export interface ProcessDefinitionSummary {
  processId: string;
  latestVersion: number;
  versions: ProcessDefinitionVersion[];
}

export interface ProcessDefinitionsListResponse {
  definitions: ProcessDefinitionSummary[];
}

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

export const processesApi = {
  /** `GET /api/definitions/list` never 404s -- a missing `process_definitions` view surfaces as an
   * empty `definitions` list (see the controller's own javadoc), which the process list page
   * renders as its empty state rather than an error. */
  list: async (): Promise<ApiResult<ProcessDefinitionsListResponse>> => {
    try {
      const response = await fetch("/api/definitions/list");
      if (!response.ok) {
        const message = (await tryExtractErrorMessage(response)) ?? `HTTP ${response.status}`;
        return { ok: false, status: response.status, message };
      }
      const data = (await response.json()) as ProcessDefinitionsListResponse;
      return { ok: true, data };
    } catch (e: unknown) {
      return { ok: false, status: null, message: e instanceof Error ? e.message : String(e) };
    }
  },
};

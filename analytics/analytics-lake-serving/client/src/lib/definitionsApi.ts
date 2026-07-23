/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Shapes mirror io.camunda.analytics.lake.serving.objects.ProcessDefinitionsService's
 * ProcessDefinitionResult record exactly -- see ProcessDefinitionsController. Kept as its own tiny
 * module (same convention as lib/objectsGraphApi.ts) rather than widening lib/api.ts.
 */

export interface ProcessDefinitionResponse {
  processId: string;
  version: number;
  bpmnXml: string;
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

export const definitionsApi = {
  /** GET /api/definitions?processId=...&version=... -- a clean 404 (view absent or no match) comes
   * back as { ok: false }, same as any other network failure; the caller decides what "not
   * available" looks like for its own view. */
  get: async (processId: string, version: number): Promise<ApiResult<ProcessDefinitionResponse>> => {
    try {
      const url = `/api/definitions?processId=${encodeURIComponent(processId)}&version=${version}`;
      const response = await fetch(url);
      if (!response.ok) {
        const message = (await tryExtractErrorMessage(response)) ?? `HTTP ${response.status}`;
        return { ok: false, status: response.status, message };
      }
      const data = (await response.json()) as ProcessDefinitionResponse;
      return { ok: true, data };
    } catch (e: unknown) {
      return { ok: false, status: null, message: e instanceof Error ? e.message : String(e) };
    }
  },
};

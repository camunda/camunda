/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type { ProblemDetails } from "@camunda/camunda-api-zod-schemas/8.10";

export type ErrorResponse<Type = "generic" | "detailed"> =
  Type extends "detailed"
    ? ProblemDetails
    : {
        error: string;
        path: string;
        status: number;
        timestamp: string;
      };

export type ApiResult<R> =
  | {
      data: R;
      error: null;
      status: number;
      success: true;
    }
  | {
      data: null;
      error: ErrorResponse | null;
      status: number;
      success: false;
    };

export type ApiPromise<R> = Promise<ApiResult<R>>;

export class ApiError extends Error {
  readonly status: number;
  readonly body: ErrorResponse | null;

  constructor(status: number, body: ErrorResponse | null) {
    super(
      body && isDetailedError(body)
        ? body.title || `Request failed with status ${status}`
        : `Request failed with status ${status}`,
    );
    this.name = "ApiError";
    this.status = status;
    this.body = body;
  }
}

export function isDetailedError(
  error: ErrorResponse,
): error is ErrorResponse<"detailed"> {
  return (
    typeof error === "object" &&
    "detail" in error &&
    "instance" in error &&
    "title" in error &&
    "type" in error
  );
}

export async function unwrap<R>(promise: ApiPromise<R>): Promise<R> {
  const result = await promise;
  if (result.success) {
    return result.data;
  }
  throw new ApiError(result.status, result.error);
}

export type ApiMethod = "GET" | "POST" | "PUT" | "DELETE" | "PATCH";

type ApiRequestParams<P> = {
  url: string;
  method: ApiMethod;
  baseUrl: string;
  params?: P;
  headers?: Record<string, string>;
};

export const pathBuilder =
  (basePath: string) =>
  (...pathComponents: (string | number)[]) => {
    if (pathComponents.length === 0) {
      return basePath;
    }
    return `${basePath}/${pathComponents.map((param) => `${encodeURIComponent(param)}`).join("/")}`;
  };

const requestUrl = (baseUrl: string, path: string, params?: unknown) => {
  let encodedParams = "";
  if (params && Object.entries(params).length > 0) {
    const urlParams = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      urlParams.append(key, value as string);
    });
    encodedParams = `?${urlParams.toString()}`;
  }

  const clearedBaseUrl = baseUrl.endsWith("/")
    ? baseUrl.substring(0, baseUrl.length - 1)
    : baseUrl;

  return `${clearedBaseUrl}${path}${encodedParams}`;
};

const apiRequest: <R, P>(
  options: ApiRequestParams<P>,
) => ApiPromise<R> = async ({ url, method, headers, params, baseUrl }) => {
  const csrfToken = sessionStorage.getItem("X-CSRF-TOKEN");
  const hasCsrfToken =
    csrfToken !== null &&
    method !== undefined &&
    ["POST", "PUT", "PATCH", "DELETE"].includes(method.toUpperCase());

  const hasBody =
    !!params &&
    ["PUT", "POST", "DELETE", "PATCH"].includes(method.toUpperCase());
  const body = hasBody ? JSON.stringify(params) : undefined;

  // default handling for content-type
  // if not set explicitly, set to application/json
  if (!headers) {
    headers = { "Content-Type": "application/json" };
  } else if (
    !Object.keys(headers).find(
      (key) => key.toLocaleLowerCase() === "content-type",
    )
  ) {
    headers["Content-Type"] = "application/json";
  }

  try {
    const response = await fetch(
      requestUrl(baseUrl || "", url, !hasBody ? params : undefined),
      {
        method,
        body,
        headers: {
          ...(hasCsrfToken ? { "X-CSRF-TOKEN": csrfToken } : {}),
          ...headers,
        },
        credentials: "include",
        redirect: "manual",
      },
    );

    let data = null;
    try {
      data = await response.json();
    } catch {
      // body is empty
    }

    if (response.ok) {
      const csrfToken = response.headers.get("X-CSRF-TOKEN");
      if (csrfToken !== null) {
        sessionStorage.setItem("X-CSRF-TOKEN", csrfToken);
      }
      return {
        data: data,
        error: null,
        status: response.status,
        success: true,
      };
    } else {
      return {
        data: null,
        error: data,
        status: response.status,
        success: false,
      };
    }
  } catch {
    return {
      data: null,
      error: null,
      status: -1,
      success: false,
    };
  }
};

export type ApiCall<R, P = undefined> = [P] extends [undefined]
  ? () => ApiPromise<R>
  : (params: P) => ApiPromise<R>;

export type ApiDefinition<R, P = undefined> = (
  params: P,
) => (baseUrl: string, headers?: Record<string, string>) => ApiPromise<R>;

const apiRequestWrapper: (
  method: ApiMethod,
) => <R, P>(
  url: string,
  params?: P,
) => (baseUrl: string, headers?: Record<string, string>) => ApiPromise<R> =
  (method: ApiMethod) => (url, params) => (baseUrl, headers) =>
    apiRequest({ baseUrl, url, method, params, headers });

export const apiGet = apiRequestWrapper("GET");

export const apiPost = apiRequestWrapper("POST");

export const apiPut = apiRequestWrapper("PUT");

export const apiDelete = apiRequestWrapper("DELETE");

export const apiPatch = apiRequestWrapper("PATCH");

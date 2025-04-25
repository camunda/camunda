/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export type ErrorResponse<Type = "generic" | "detailed"> =
  Type extends "detailed"
    ? {
        detail: string;
        instance: string;
        status: number;
        title: string;
        type: string;
      }
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

type Handler = (response: Response) => void | Promise<void>;
const handlers: Handler[] = [];

export function addHandler(fct: Handler) {
  if (typeof fct === "function") {
    handlers.push(fct);
  } else {
    throw new Error("Handler must be a function");
  }
}

export function removeHandler(fct: Handler) {
  const index = handlers.indexOf(fct);
  if (index !== -1) {
    handlers.splice(index, 1);
  }
}

const apiRequest: <R, P>(
  options: ApiRequestParams<P>,
) => ApiPromise<R> = async ({ url, method, headers, params, baseUrl }) => {
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
        headers,
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

    for (const handler of handlers) {
      await handler(response);
      return { data, error: null, status: 200, success: true };
    }

    if (response.ok) {
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

export type ApiCall<R, P = undefined> = P extends undefined
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

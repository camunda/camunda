export type ApiResult<R> =
  | {
      data: R;
      errors: null;
      status: number;
      success: true;
    }
  | {
      data: null;
      errors: string[];
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

const apiRequest: <R, P>(
  options: ApiRequestParams<P>,
) => ApiPromise<R> = async ({ url, method, headers, params, baseUrl }) => {
  const hasBody =
    !!params && ["PUT", "POST", "PATCH"].includes(method.toUpperCase());
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
      },
    );
    let data = null;
    try {
      data = await response.json();
    } catch {
      // body is empty
    }
    const success = response.ok;

    return {
      data: success ? data : null,
      errors: success ? null : (data?.errors ?? []),
      status: response.status,
      success,
    };
  } catch {
    return {
      data: null,
      status: -1,
      errors: [],
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

export const apiPatch = apiRequestWrapper("PATCH");

export const apiDelete = apiRequestWrapper("DELETE");

export const namedErrorsReducer = (
  result: Record<string, string[]>,
  current: string,
): Record<string, string[]> => {
  const name = current.split(".")[0];
  return {
    ...result,
    [name]: [...(result[name] || []), current],
  };
};

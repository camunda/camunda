/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export function parseArrayParam<T = string>(value: string | null): Array<T> {
  return value ? (value.split(",") as Array<T>) : [];
}

export function buildArrayParam<T = string>(
  value: Array<T> | undefined,
): string | null {
  return value && value.length ? value.join(",") : null;
}

type FieldParser<V> = {
  parse: (params: URLSearchParams) => V;
  serialize: (value: V, params: URLSearchParams) => void;
};

export function createSearchParamsSync<T>(config: {
  [K in keyof T]: FieldParser<T[K]>;
}) {
  function parse(search: string): T {
    const params = new URLSearchParams(search);
    const result = {} as T;

    for (const key in config) {
      result[key] = config[key].parse(params);
    }

    return result;
  }

  function serialize(filters: T): string {
    const params = new URLSearchParams();

    for (const key in config) {
      config[key].serialize(filters[key], params);
    }

    return params.toString();
  }

  return { parse, serialize };
}

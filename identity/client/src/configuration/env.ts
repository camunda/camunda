/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

interface AppWindow {
  clientConfig: Record<string, string>;
}

const { clientConfig } = window as unknown as AppWindow;

export type GetEnv<R = string | undefined> = (
  key: string,
  defaultValue?: R,
) => R;

const getEnv = <R>(
  key: string,
  defaultValue: R,
  parser: (value: string) => R,
): R => {
  const viteKey = `VITE_${key}`;
  if (clientConfig && clientConfig[viteKey] !== undefined) {
    return parser(clientConfig[viteKey]);
  }
  if (import.meta.env[viteKey] !== undefined) {
    return parser(import.meta.env[viteKey] as string);
  }
  return defaultValue;
};

export const getEnvBoolean: GetEnv<boolean> = (key, defaultValue = false) =>
  getEnv(key, defaultValue, (value) => value === "true");

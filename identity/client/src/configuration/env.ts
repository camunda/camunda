/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda
 * Services GmbH under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file except in compliance with the Camunda License 1.0.
 */

interface AppWindow {
  env: Record<string, string>;
}

const { env } = window as unknown as AppWindow;

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
  if (env && env[viteKey] !== undefined) {
    return parser(env[viteKey]);
  }
  if (import.meta.env[viteKey] !== undefined) {
    return parser(import.meta.env[viteKey] as string);
  }
  return defaultValue;
};

export const getEnvString: GetEnv<string> = (key, defaultValue = "") =>
  getEnv(key, defaultValue, (value) => value);

export const getEnvBoolean: GetEnv<boolean> = (key, defaultValue = false) =>
  getEnv(key, defaultValue, (value) => value === "true");

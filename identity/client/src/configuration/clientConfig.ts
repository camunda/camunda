/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export type GetClientConfig<R = string | undefined> = (
  key: keyof ClientConfig,
  defaultValue?: R,
) => R;

const getClientConfigValue = <R>(
  key: keyof ClientConfig,
  defaultValue: R,
  parser: (value: string) => R,
): R => {
  const clientValue = window.clientConfig?.[key];
  if (clientValue !== undefined) {
    return parser(clientValue);
  }
  return defaultValue;
};

export const getClientConfigBoolean: GetClientConfig<boolean> = (
  key,
  defaultValue = false,
) => getClientConfigValue(key, defaultValue, (value) => value === "true");

export const getClientConfigString: GetClientConfig<string | undefined> = (
  key,
  defaultValue,
) => {
  return getClientConfigValue<string | undefined>(
    key,
    defaultValue,
    (value) => value,
  );
};

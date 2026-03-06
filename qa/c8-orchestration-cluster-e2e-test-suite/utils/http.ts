/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export type Credentials = {
  baseUrl: string;
  accessToken: string;
};

export const credentials: Credentials = {
  baseUrl: process.env.CORE_APPLICATION_OPERATE_URL ?? 'http://localhost:8081',
  accessToken: encode(
    `${process.env.TEST_USERNAME ?? 'demo'}:${process.env.TEST_PASSWORD ?? 'demo'}`,
  ),
};

export function encode(auth: string) {
  return Buffer.from(auth).toString('base64');
}

export function authHeaders(token?: string): Record<string, string> {
  const h: Record<string, string> = {};
  if (token) h.Authorization = `Basic ${token}`;
  return h;
}

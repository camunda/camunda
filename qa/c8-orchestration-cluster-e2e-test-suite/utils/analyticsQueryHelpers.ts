/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {APIRequestContext} from '@playwright/test';

const defaultLokiBaseUrl = process.env.LOKI_URL ?? 'http://localhost:3100';
const defaultPrometheusBaseUrl =
  process.env.PROMETHEUS_URL ?? 'http://localhost:9090';

export type LokiStreamResult = {
  stream: Record<string, string>;
  values: [string, string][];
};

export type LokiQueryRangeResponse = {
  status: string;
  data: {
    resultType: string;
    result: LokiStreamResult[];
  };
};

export type PrometheusQueryResponse = {
  status: string;
  data: {
    resultType: string;
    result: Array<{
      metric: Record<string, string>;
      value: [number, string];
    }>;
  };
};

/**
 * Queries Loki's HTTP API directly (no Grafana UI). Returns the raw
 * "streams" result array: one entry per unique label-set, each carrying
 * that record's structured metadata under `stream` and its timestamped
 * (empty-body) log lines under `values`.
 *
 * `baseUrl` defaults to the shared stack's Loki (LOKI_URL env var, or
 * localhost:3100). Pass it explicitly to target a different instance, e.g.
 * the isolated environment's Loki on a different port.
 */
export async function queryLoki(
  request: APIRequestContext,
  query: string,
  startNs: string,
  endNs: string,
  limit = 1000,
  baseUrl: string = defaultLokiBaseUrl,
): Promise<LokiStreamResult[]> {
  const response = await request.get(`${baseUrl}/loki/api/v1/query_range`, {
    params: {query, start: startNs, end: endNs, limit: String(limit)},
  });
  if (!response.ok()) {
    throw new Error(
      `Loki query failed: ${response.status()} ${await response.text()}`,
    );
  }
  const body = (await response.json()) as LokiQueryRangeResponse;
  if (body.status !== 'success' || !body.data) {
    throw new Error(
      `Loki query returned non-success response: ${JSON.stringify(body)}`,
    );
  }
  return body.data.result;
}

/** Total number of log lines returned across every stream in the result. */
export function countLokiEntries(streams: LokiStreamResult[]): number {
  return streams.reduce((sum, s) => sum + s.values.length, 0);
}

/**
 * Queries Prometheus's instant-query HTTP API directly (no Grafana UI).
 * Returns the raw result array; each entry's `value` is `[timestamp, "asString"]`.
 *
 * `baseUrl` defaults to the shared stack's Prometheus (PROMETHEUS_URL env
 * var, or localhost:9090). Pass it explicitly to target a different instance.
 */
export async function queryPrometheus(
  request: APIRequestContext,
  promQL: string,
  baseUrl: string = defaultPrometheusBaseUrl,
): Promise<PrometheusQueryResponse['data']['result']> {
  const response = await request.get(`${baseUrl}/api/v1/query`, {
    params: {query: promQL},
  });
  if (!response.ok()) {
    throw new Error(
      `Prometheus query failed: ${response.status()} ${await response.text()}`,
    );
  }
  const body = (await response.json()) as PrometheusQueryResponse;
  if (body.status !== 'success' || !body.data) {
    throw new Error(
      `Prometheus query returned non-success response: ${JSON.stringify(body)}`,
    );
  }
  return body.data.result;
}

/** Convenience: the scalar value of a single-series instant query, or undefined if empty. */
export function firstPrometheusValue(
  result: PrometheusQueryResponse['data']['result'],
): number | undefined {
  const raw = result[0]?.value?.[1];
  return raw === undefined ? undefined : Number(raw);
}

/** Converts a JS Date (or now) to the nanosecond-string timestamp Loki's API expects. */
export function toLokiNanos(date: Date = new Date()): string {
  return `${date.getTime()}000000`;
}

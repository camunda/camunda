/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const MISSING_VALUE = "–";

/** Formats an ISO date-time like the operations log does; "–" when missing. */
export const formatDateTime = (value?: string | null): string => {
  if (!value) return MISSING_VALUE;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? MISSING_VALUE : date.toLocaleString();
};

export type AttributeDisplayValue =
  { kind: "text"; text: string } | { kind: "json"; text: string };

const isScalar = (value: unknown): value is string | number | boolean =>
  typeof value === "string" ||
  typeof value === "number" ||
  typeof value === "boolean";

/**
 * Scalars are shown as text, arrays of scalars comma-joined; objects and
 * arrays containing objects as compact pretty-printed JSON.
 */
export const toAttributeDisplayValue = (
  value: unknown,
): AttributeDisplayValue => {
  if (value === null || value === undefined) {
    return { kind: "text", text: MISSING_VALUE };
  }
  if (isScalar(value)) {
    return { kind: "text", text: String(value) };
  }
  if (Array.isArray(value) && value.every(isScalar)) {
    return { kind: "text", text: value.join(", ") };
  }
  return { kind: "json", text: JSON.stringify(value, null, 2) };
};

export const getAttributeEntries = (
  attributes?: Record<string, unknown> | null,
): [string, AttributeDisplayValue][] =>
  Object.entries(attributes ?? {}).map(([name, value]) => [
    name,
    toAttributeDisplayValue(value),
  ]);

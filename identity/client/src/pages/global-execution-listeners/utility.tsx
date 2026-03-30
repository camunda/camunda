/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type { TFunction } from "i18next";

export const LISTENER_TYPE_PATTERN = /^[a-zA-Z0-9._-]+$/;

export type ExecutionListenerEventType = "all" | "start" | "end";

export const EXECUTION_LISTENER_EVENT_TYPES: ExecutionListenerEventType[] = [
  "all",
  "start",
  "end",
];

export const ELEMENT_CATEGORIES = [
  "all",
  "tasks",
  "gateways",
  "events",
  "containers",
] as const;

export type ElementCategory = (typeof ELEMENT_CATEGORIES)[number];

export const getEventTypeLabel = (
  eventType: ExecutionListenerEventType,
  t: TFunction<string, string>,
): string => {
  const labels: Record<ExecutionListenerEventType, string> = {
    all: t("eventTypeAll"),
    start: t("eventTypeStart"),
    end: t("eventTypeEnd"),
  };
  return labels[eventType];
};

export const getEventTypeLabels = (
  eventTypes: ExecutionListenerEventType[],
  t: TFunction<string, string>,
): string => {
  return eventTypes.includes("all")
    ? t("eventTypeAll")
    : eventTypes.map((et) => getEventTypeLabel(et, t)).join(", ");
};

export const getCategoryLabel = (
  category: string,
  t: TFunction<string, string>,
): string => {
  const labels: Record<string, string> = {
    all: t("categoryAll"),
    tasks: t("categoryTasks"),
    gateways: t("categoryGateways"),
    events: t("categoryEvents"),
    containers: t("categoryContainers"),
  };
  return labels[category] ?? category;
};

export const getCategoryLabels = (
  categories: string[],
  t: TFunction<string, string>,
): string => {
  if (categories.length === 0) return t("categoryAll");
  return categories.includes("all")
    ? t("categoryAll")
    : categories.map((c) => getCategoryLabel(c, t)).join(", ");
};

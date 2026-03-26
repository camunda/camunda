/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type { GlobalTaskListener } from "@camunda/camunda-api-zod-schemas/8.10";
import type { TFunction } from "i18next";

export const LISTENER_TYPE_PATTERN = /^[a-zA-Z0-9._-]+$/;

export const getEventTypeLabel = (
  eventType: GlobalTaskListener["eventTypes"][number],
  t: TFunction<string, string>,
): string => {
  const labels: Record<GlobalTaskListener["eventTypes"][number], string> = {
    all: t("eventTypeAll"),
    creating: t("eventTypeCreating"),
    updating: t("eventTypeUpdating"),
    assigning: t("eventTypeAssigning"),
    completing: t("eventTypeCompleting"),
    canceling: t("eventTypeCanceling"),
  };
  return labels[eventType];
};

export const getEventTypeLabels = (
  eventTypes: GlobalTaskListener["eventTypes"],
  t: TFunction<string, string>,
): string => {
  return eventTypes.includes("all")
    ? t("eventTypeAll")
    : eventTypes.map((et) => getEventTypeLabel(et, t)).join(", ");
};

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type { GlobalExecutionListenerEventType } from "src/utility/api/global-execution-listeners";
import type { TFunction } from "i18next";

export const LISTENER_TYPE_PATTERN = /^[a-zA-Z0-9._-]+$/;

export const getEventTypeLabel = (
  eventType: GlobalExecutionListenerEventType,
  t: TFunction<string, string>,
): string => {
  const labels: Record<GlobalExecutionListenerEventType, string> = {
    start: t("eventTypeStart"),
    end: t("eventTypeEnd"),
  };
  return labels[eventType];
};

export const getEventTypeLabels = (
  eventTypes: GlobalExecutionListenerEventType[],
  t: TFunction<string, string>,
): string => {
  return eventTypes.map((et) => getEventTypeLabel(et, t)).join(", ");
};

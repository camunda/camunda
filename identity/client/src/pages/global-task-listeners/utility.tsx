/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  CreateGlobalTaskListenerRequestBody,
  GlobalTaskListener,
  GlobalTaskListenerEventType,
} from "@camunda/camunda-api-zod-schemas/8.10";
import type { TFunction } from "i18next";
import { LISTENER_EVENT_TYPES } from "src/utility/api/global-task-listeners";

export const LISTENER_TYPE_PATTERN = /^[a-zA-Z0-9._-]+$/;

const INDIVIDUAL_EVENT_TYPES = LISTENER_EVENT_TYPES.filter(
  (eventType) => eventType !== "all",
);

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

export const getEventTypeOptions = (t: TFunction<string, string>) =>
  LISTENER_EVENT_TYPES.map((eventType) => ({
    value: eventType,
    label: getEventTypeLabel(eventType, t),
  }));

/** Value options for the `Select` input, which is string based. */
export const getExecutionOrderOptions = (t: TFunction<string, string>) => [
  { value: "false", label: t("executionOrderBefore") },
  { value: "true", label: t("executionOrderAfter") },
];

/**
 * Keeps the "all" option in lockstep with the individual ones: toggling it
 * selects or clears every event type, and checking the last individual type
 * checks "all" alongside them.
 *
 * `selected` is `MultiSelect`'s raw `string[]`. Filtering `LISTENER_EVENT_TYPES`
 * by it both narrows the type and restores the schema's order, which carries
 * meaning here — "all" is a real API value listed among the individual events,
 * not a select-all affordance.
 */
export const syncAllEventType = (
  selected: string[],
  previous: GlobalTaskListenerEventType[],
): GlobalTaskListenerEventType[] => {
  const next = LISTENER_EVENT_TYPES.filter((eventType) =>
    selected.includes(eventType),
  );

  if (next.includes("all") !== previous.includes("all")) {
    return next.includes("all") ? [...LISTENER_EVENT_TYPES] : [];
  }

  const individual = next.filter((eventType) => eventType !== "all");
  return individual.length === INDIVIDUAL_EVENT_TYPES.length
    ? [...LISTENER_EVENT_TYPES]
    : individual;
};

/**
 * "all" already means every event type to the API, so it is sent on its own
 * rather than next to the individual values the form keeps checked to render
 * them as selected.
 */
export const toRequestEventTypes = (
  eventTypes: GlobalTaskListenerEventType[],
): GlobalTaskListenerEventType[] =>
  eventTypes.includes("all")
    ? ["all"]
    : eventTypes.filter((eventType) => eventType !== "all");

/**
 * Counterpart to `toRequestEventTypes`: a listener stored as `["all"]` is
 * expanded into every event type, because that is the state the form keeps
 * once "All events" is checked — see {@link syncAllEventType}.
 *
 * Without expanding, the loaded state differs from the one any interaction
 * produces: the dropdown shows "All events" alone, and checking a single event
 * on top of it drops "all" and leaves that one event as the whole selection.
 */
const toFormEventTypes = (
  eventTypes: GlobalTaskListenerEventType[],
): GlobalTaskListenerEventType[] =>
  eventTypes.includes("all")
    ? [...LISTENER_EVENT_TYPES]
    : LISTENER_EVENT_TYPES.filter((eventType) =>
        eventTypes.includes(eventType),
      );

export const toFormValues = (
  listener: GlobalTaskListener,
): CreateGlobalTaskListenerRequestBody => ({
  id: listener.id,
  type: listener.type,
  eventTypes: toFormEventTypes(listener.eventTypes),
  retries: listener.retries ?? undefined,
  afterNonGlobal: listener.afterNonGlobal ?? undefined,
  priority: listener.priority ?? undefined,
});

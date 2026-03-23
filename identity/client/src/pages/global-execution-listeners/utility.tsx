/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type { TFunction } from "i18next";
import type {
  GlobalExecutionListenerEventType,
  GlobalExecutionListenerElementType,
  GlobalExecutionListenerCategory,
} from "src/utility/api/global-execution-listeners";

export const LISTENER_TYPE_PATTERN = /^[a-zA-Z0-9._-]+$/;

const EVENT_TYPE_LABEL_KEYS: Record<GlobalExecutionListenerEventType, string> =
  {
    all: "eventTypeAll",
    start: "eventTypeStart",
    end: "eventTypeEnd",
    cancel: "eventTypeCancel",
  };

export const getEventTypeLabel = (
  eventType: GlobalExecutionListenerEventType,
  t: TFunction<string, string>,
): string => {
  return t(EVENT_TYPE_LABEL_KEYS[eventType]);
};

export const getEventTypeLabels = (
  eventTypes: GlobalExecutionListenerEventType[],
  t: TFunction<string, string>,
): string => {
  return eventTypes.includes("all")
    ? t("eventTypeAll")
    : eventTypes.map((et) => getEventTypeLabel(et, t)).join(", ");
};

const ELEMENT_TYPE_LABEL_KEYS: Record<
  GlobalExecutionListenerElementType,
  string
> = {
  process: "elementTypeProcess",
  subprocess: "elementTypeSubprocess",
  eventSubprocess: "elementTypeEventSubprocess",
  serviceTask: "elementTypeServiceTask",
  userTask: "elementTypeUserTask",
  sendTask: "elementTypeSendTask",
  receiveTask: "elementTypeReceiveTask",
  scriptTask: "elementTypeScriptTask",
  businessRuleTask: "elementTypeBusinessRuleTask",
  callActivity: "elementTypeCallActivity",
  multiInstanceBody: "elementTypeMultiInstanceBody",
  exclusiveGateway: "elementTypeExclusiveGateway",
  parallelGateway: "elementTypeParallelGateway",
  inclusiveGateway: "elementTypeInclusiveGateway",
  eventBasedGateway: "elementTypeEventBasedGateway",
  startEvent: "elementTypeStartEvent",
  endEvent: "elementTypeEndEvent",
  intermediateCatchEvent: "elementTypeIntermediateCatchEvent",
  intermediateThrowEvent: "elementTypeIntermediateThrowEvent",
  boundaryEvent: "elementTypeBoundaryEvent",
};

export const getElementTypeLabel = (
  elementType: GlobalExecutionListenerElementType,
  t: TFunction<string, string>,
): string => {
  return t(ELEMENT_TYPE_LABEL_KEYS[elementType]);
};

export const getElementTypeLabels = (
  elementTypes: GlobalExecutionListenerElementType[],
  t: TFunction<string, string>,
): string => {
  return elementTypes.map((et) => getElementTypeLabel(et, t)).join(", ");
};

const CATEGORY_LABEL_KEYS: Record<GlobalExecutionListenerCategory, string> = {
  all: "categoryAll",
  tasks: "categoryTasks",
  gateways: "categoryGateways",
  events: "categoryEvents",
};

export const getCategoryLabel = (
  category: GlobalExecutionListenerCategory,
  t: TFunction<string, string>,
): string => {
  return t(CATEGORY_LABEL_KEYS[category]);
};

export const getCategoryLabels = (
  categories: GlobalExecutionListenerCategory[],
  t: TFunction<string, string>,
): string => {
  return categories.includes("all")
    ? t("categoryAll")
    : categories.map((c) => getCategoryLabel(c, t)).join(", ");
};

export const getElementScopeLabel = (
  elementTypes: GlobalExecutionListenerElementType[] | undefined,
  categories: GlobalExecutionListenerCategory[] | undefined,
  t: TFunction<string, string>,
): string => {
  if (
    categories?.includes("all") ||
    (!elementTypes?.length && !categories?.length)
  ) {
    return t("allElements");
  }

  const parts: string[] = [];
  if (categories?.length) {
    parts.push(categories.map((c) => getCategoryLabel(c, t)).join(", "));
  }
  if (elementTypes?.length) {
    parts.push(getElementTypeLabels(elementTypes, t));
  }
  return parts.join(", ");
};

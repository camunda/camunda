/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  GlobalExecutionListener,
  GlobalExecutionListenerEventType,
  GlobalExecutionListenerElementType,
  GlobalExecutionListenerCategory,
} from "@camunda/camunda-api-zod-schemas/8.9";
import type { TFunction } from "i18next";

export const LISTENER_TYPE_PATTERN = /^[a-zA-Z0-9._-]+$/;

export const getEventTypeLabel = (
  eventType: GlobalExecutionListenerEventType,
  t: TFunction<string, string>,
): string => {
  const labels: Record<GlobalExecutionListenerEventType, string> = {
    all: t("eventTypeAll"),
    start: t("eventTypeStart"),
    end: t("eventTypeEnd"),
  };
  return labels[eventType];
};

export const getEventTypeLabels = (
  eventTypes: GlobalExecutionListener["eventTypes"],
  t: TFunction<string, string>,
): string => {
  return eventTypes.includes("all")
    ? t("eventTypeAll")
    : eventTypes.map((et) => getEventTypeLabel(et, t)).join(", ");
};

export const getElementTypeLabel = (
  elementType: GlobalExecutionListenerElementType,
  t: TFunction<string, string>,
): string => {
  const labels: Record<GlobalExecutionListenerElementType, string> = {
    all: t("elementTypeAll"),
    serviceTask: t("elementTypeServiceTask"),
    userTask: t("elementTypeUserTask"),
    sendTask: t("elementTypeSendTask"),
    receiveTask: t("elementTypeReceiveTask"),
    businessRuleTask: t("elementTypeBusinessRuleTask"),
    scriptTask: t("elementTypeScriptTask"),
    callActivity: t("elementTypeCallActivity"),
    subProcess: t("elementTypeSubProcess"),
    eventSubProcess: t("elementTypeEventSubProcess"),
    multiInstanceBody: t("elementTypeMultiInstanceBody"),
    exclusiveGateway: t("elementTypeExclusiveGateway"),
    inclusiveGateway: t("elementTypeInclusiveGateway"),
    parallelGateway: t("elementTypeParallelGateway"),
    eventBasedGateway: t("elementTypeEventBasedGateway"),
    startEvent: t("elementTypeStartEvent"),
    endEvent: t("elementTypeEndEvent"),
    intermediateThrowEvent: t("elementTypeIntermediateThrowEvent"),
    intermediateCatchEvent: t("elementTypeIntermediateCatchEvent"),
    boundaryEvent: t("elementTypeBoundaryEvent"),
  };
  return labels[elementType] ?? elementType;
};

export const getCategoryLabel = (
  category: GlobalExecutionListenerCategory,
  t: TFunction<string, string>,
): string => {
  const labels: Record<GlobalExecutionListenerCategory, string> = {
    all: t("categoryAll"),
    tasks: t("categoryTasks"),
    gateways: t("categoryGateways"),
    events: t("categoryEvents"),
    containers: t("categoryContainers"),
  };
  return labels[category] ?? category;
};

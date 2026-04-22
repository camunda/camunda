/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useMemo } from "react";
import { z } from "zod";
import { useApi, usePagination } from "src/utility/api";
import {
  searchMessageSubscriptions,
  type MessageSubscription,
  type QueryMessageSubscriptionsRequestBody,
} from "src/utility/api/message-subscriptions";

type Sort = NonNullable<QueryMessageSubscriptionsRequestBody["sort"]>;
const DEFAULT_SORT: Sort = [{ field: "toolName", order: "asc" }];

const toolString = z.string().trim().min(1).nullable().default(null);
const toolDataSchema = z
  .looseObject({
    "io.camunda.tool:purpose": toolString,
    "io.camunda.tool:results": toolString,
    "io.camunda.tool:when_to_use": toolString,
    "io.camunda.tool:when_not_to_use": toolString,
  })
  .transform((data) => ({
    purpose: data["io.camunda.tool:purpose"],
    results: data["io.camunda.tool:results"],
    whenToUse: data["io.camunda.tool:when_to_use"],
    whenNotToUse: data["io.camunda.tool:when_not_to_use"],
  }));

export type McpProcessTool = {
  id: string;
  toolName: string;
  toolDescription: string;
  toolData: z.output<typeof toolDataSchema>;
  processDefinitionKey: string;
  processDefinitionId: string;
  processDefinitionName: string;
  processDefinitionVersion: number | string;
  tenantId: string;
};

const mapSubscriptionToProcessTool = (
  sub: MessageSubscription,
): McpProcessTool | null => {
  // `toolName` is expected to exist based on the filter supplied to the backend.
  if (!sub.toolName) return null;

  const toolData = toolDataSchema.parse(sub.extensionProperties ?? {});
  return {
    id: sub.messageSubscriptionKey,
    toolName: sub.toolName,
    toolDescription: toolData.purpose ?? "-",
    toolData,
    processDefinitionKey: sub.processDefinitionKey,
    processDefinitionId: sub.processDefinitionId,
    processDefinitionName: sub.processDefinitionName ?? sub.processDefinitionId,
    processDefinitionVersion: sub.processDefinitionVersion ?? "-",
    tenantId: sub.tenantId,
  };
};

export const useMcpProcessTools = () => {
  const { pageParams, page, ...paginationRest } = usePagination();

  const searchTermsFilter = useMemo(() => {
    const term = pageParams.filter?.toolName?.trim();
    if (!term) return undefined;
    return { toolName: { $like: `*${term}*` } };
  }, [pageParams.filter]);

  const { data, ...apiRest } = useApi(searchMessageSubscriptions, {
    sort: (pageParams.sort ?? DEFAULT_SORT) as Sort,
    filter: {
      messageSubscriptionType: "START_EVENT",
      messageSubscriptionState: { $neq: "DELETED" },
      toolName: { $exists: true },
      ...searchTermsFilter,
    },
    page: {
      from: pageParams.page.from,
      limit: pageParams.page.limit,
    },
  });

  const processTools = useMemo<McpProcessTool[]>(() => {
    if (!data?.items || data.items.length === 0) {
      return [];
    }

    return data.items
      .map(mapSubscriptionToProcessTool)
      .filter((p) => p !== null);
  }, [data]);

  return {
    processTools,
    page: { ...page, ...data?.page },
    ...paginationRest,
    ...apiRest,
  };
};

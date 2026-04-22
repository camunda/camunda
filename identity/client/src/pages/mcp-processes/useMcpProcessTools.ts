/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  MessageSubscription,
  QueryMessageSubscriptionsRequestBody,
} from "@camunda/camunda-api-zod-schemas/8.10";
import { useMemo } from "react";
import { useApi, usePagination } from "src/utility/api";
import { searchMessageSubscriptions } from "src/utility/api/message-subscriptions";

const TOOL_PURPOSE_KEY = "io.camunda.tool:purpose";

type Sort = NonNullable<QueryMessageSubscriptionsRequestBody["sort"]>;
const DEFAULT_SORT: Sort = [{ field: "toolName", order: "asc" }];

export type McpProcessTool = {
  id: string;
  toolName: string;
  toolDescription: string;
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

  return {
    id: sub.messageSubscriptionKey,
    toolName: sub.toolName,
    toolDescription: sub.extensionProperties?.[TOOL_PURPOSE_KEY] ?? "-",
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

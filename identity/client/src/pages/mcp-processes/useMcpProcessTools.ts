/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useCallback, useMemo } from "react";
import { usePaginatedApi } from "src/utility/api";
import {
  searchMessageSubscriptions,
  type MessageSubscription,
} from "src/utility/api/message-subscriptions";

const TOOL_PURPOSE_KEY = "io.camunda.tool:purpose";

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
  // `toolName` is expected to exists based on the filter supplied to the backend.
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
  const { data, setSearch, ...rest } = usePaginatedApi(
    searchMessageSubscriptions,
    {
      filter: {
        messageSubscriptionType: "START_EVENT",
        messageSubscriptionState: { $neq: "DELETED" },
        toolName: { $exists: true },
      },
    },
  );

  const handleSearch = useCallback(
    (value: Record<string, string> | undefined) => {
      const term = value?.toolName?.trim();
      if (!term) {
        setSearch(undefined);
        return;
      }
      // `setSearch` is typed as `Record<string, string>`, but the value is
      // merged into the request `filter`, which supports advanced string
      // filters like `$like`.
      setSearch({
        toolName: { $like: `*${term}*` },
      } as unknown as Record<string, string>);
    },
    [setSearch],
  );

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
    setSearch: handleSearch,
    ...rest,
  };
};

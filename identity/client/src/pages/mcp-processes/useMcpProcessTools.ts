/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useMemo } from "react";
import { z } from "zod";
import { usePaginatedApi } from "src/utility/api";
import {
  searchMessageSubscriptions,
  type MessageSubscription,
} from "src/utility/api/message-subscriptions";

const mcpExtensionPropertiesSchema = z.object({
  "io.camunda.tool:name": z.string(),
  "io.camunda.tool:purpose": z.string(),
});

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
  const result = mcpExtensionPropertiesSchema.safeParse(
    sub.extensionProperties,
  );
  if (!result.success) return null;

  return {
    id: sub.messageSubscriptionKey,
    toolName: result.data["io.camunda.tool:name"],
    toolDescription: result.data["io.camunda.tool:purpose"],
    processDefinitionKey: sub.processDefinitionKey,
    processDefinitionId: sub.processDefinitionId,
    processDefinitionName: sub.processDefinitionName ?? sub.processDefinitionId,
    processDefinitionVersion: sub.processDefinitionVersion ?? "-",
    tenantId: sub.tenantId,
  };
};

export const useMcpProcessTools = () => {
  const { data, ...rest } = usePaginatedApi(searchMessageSubscriptions, {
    filter: { messageSubscriptionType: "START_EVENT" },
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
    ...rest,
  };
};

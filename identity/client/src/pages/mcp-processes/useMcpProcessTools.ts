/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useMemo } from "react";
import { z } from "zod";
import { useApi } from "src/utility/api";
import {
  searchMessageSubscriptions,
  type MessageSubscription,
} from "src/utility/api/message-subscriptions";

const mcpExtensionPropertiesSchema = z.object({
  "io.camunda.tool:name": z.string(),
  "io.camunda.tool:purpose": z.string(),
});

export interface ProcessTool {
  toolKey: string;
  toolName: string;
  toolDescription: string;
  processDefinitionKey: string;
  processDefinitionName: string;
  processDefinitionVersion: number | null;
  tenantId: string | null;
}

const mapSubscriptionToProcessTool = (
  sub: MessageSubscription,
): ProcessTool | null => {
  const result = mcpExtensionPropertiesSchema.safeParse(
    sub.extensionProperties,
  );
  if (!result.success) return null;

  return {
    toolKey: sub.messageSubscriptionKey,
    toolName: result.data["io.camunda.tool:name"],
    toolDescription: result.data["io.camunda.tool:purpose"],
    processDefinitionKey: sub.processDefinitionKey,
    processDefinitionName: sub.processDefinitionName ?? sub.processDefinitionId,
    processDefinitionVersion: sub.processDefinitionVersion,
    tenantId: sub.tenantId,
  };
};

export const useMcpProcessTools = () => {
  const { data, loading, success, error, reload } = useApi(
    searchMessageSubscriptions,
    { filter: { messageSubscriptionType: "START_EVENT" } },
  );

  const processTools = useMemo<ProcessTool[]>(() => {
    if (!data?.items || data.items.length === 0) {
      return [];
    }

    return data?.items
      .map(mapSubscriptionToProcessTool)
      .filter((p) => p !== null);
  }, [data]);

  return { processTools, loading, success, error, reload };
};

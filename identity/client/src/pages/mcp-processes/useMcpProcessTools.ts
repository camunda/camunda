/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useMemo } from "react";
import { useApi } from "src/utility/api";
import { searchMessageSubscriptions } from "src/utility/api/message-subscriptions";

const MCP_TOOL_NAME_PROPERTY = "io.camunda.tool:name";

export const useMcpProcessTools = () => {
  const { data, loading, success, error, reload } = useApi(
    searchMessageSubscriptions,
    { filter: { messageSubscriptionType: "START_EVENT" } },
  );

  const processTools = useMemo(
    () =>
      data?.items.filter(
        (sub) => MCP_TOOL_NAME_PROPERTY in (sub.extensionProperties ?? {}),
      ) ?? [],
    [data],
  );

  return { processTools, loading, success, error, reload };
};

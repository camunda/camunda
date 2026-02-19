/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {APIResponse, request} from '@playwright/test';

const MCP_SERVER_URL = process.env.MCP_SERVER_URL || 'http://localhost:12001';

/*
 * Validate that the MCP test server is healthy and ready to accept requests.
 *
 * Optionally allows overriding the MCP server URL.
 */

export async function validateMcpServerHealth(
  serverUrl?: string,
): Promise<APIResponse> {
  const mcpServerUrl = serverUrl || MCP_SERVER_URL;
  const healthEndpoint = `${mcpServerUrl}/actuator/health`;

  const apiRequestContext = await request.newContext();

  try {
    const response = await apiRequestContext.get(healthEndpoint);

    if (response.status() !== 200) {
      const errorBody = await response.text();
      throw new Error(
        `MCP server health check failed with status ${response.status()}. Response: ${errorBody}`,
      );
    }

    return response;
  } catch (error: unknown) {
    const errorMessage =
      error instanceof Error ? error.message : 'Unknown error';
    throw new Error(
      `MCP server is not available at ${healthEndpoint}. ` +
        `Error: ${errorMessage}. ` +
        'Please ensure the MCP docker container is running with: ' +
        'docker run -d --name mcp-test-server --network host registry.camunda.cloud/mcp/mcp-test-server:latest',
    );
  } finally {
    await apiRequestContext.dispose();
  }
}

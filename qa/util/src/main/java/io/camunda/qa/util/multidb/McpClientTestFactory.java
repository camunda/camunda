/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.multidb;

import io.camunda.qa.util.auth.Authenticated;
import io.camunda.zeebe.qa.util.cluster.TestGateway;
import io.modelcontextprotocol.client.McpSyncClient;

public interface McpClientTestFactory extends AutoCloseable {

  /** Returns an MCP client for the given gateway and authenticated user */
  McpSyncClient getMcpClient(final TestGateway<?> gateway, final Authenticated authenticated);
}

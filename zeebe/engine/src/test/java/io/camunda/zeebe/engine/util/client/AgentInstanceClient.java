/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

/**
 * Fluent test client for agent instance commands.
 *
 * <p>This commit only provides the constructor wired through {@link
 * io.camunda.zeebe.engine.util.EngineRule#agentInstances()}; the CREATE builder lands with the
 * first test that exercises it.
 */
@SuppressWarnings("unused")
public final class AgentInstanceClient {

  private final CommandWriter writer;

  public AgentInstanceClient(final CommandWriter writer) {
    this.writer = writer;
  }
}

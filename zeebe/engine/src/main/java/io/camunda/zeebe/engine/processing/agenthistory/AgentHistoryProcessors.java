/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.agenthistory;

import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public final class AgentHistoryProcessors {

  private AgentHistoryProcessors() {}

  public static void addAgentHistoryProcessors(
      final KeyGenerator keyGenerator,
      final TypedRecordProcessors typedRecordProcessors,
      final Writers writers,
      final AuthorizationCheckBehavior authCheckBehavior,
      final ProcessingState processingState) {
    typedRecordProcessors.onCommand(
        ValueType.AGENT_HISTORY,
        AgentHistoryIntent.CREATE,
        new AgentHistoryCreateProcessor(writers, processingState, authCheckBehavior, keyGenerator));
  }
}

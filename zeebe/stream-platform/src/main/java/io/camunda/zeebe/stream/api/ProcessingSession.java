/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.api;

import static io.camunda.zeebe.protocol.record.RecordMetadataDecoder.batchOperationReferenceNullValue;
import static io.camunda.zeebe.protocol.record.RecordMetadataDecoder.operationReferenceNullValue;

import io.camunda.zeebe.protocol.impl.encoding.AgentInfo;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.encoding.InternalOperationReference;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.Agent;
import io.camunda.zeebe.protocol.record.ChannelType;
import io.camunda.zeebe.protocol.record.RecordType;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

public interface ProcessingSession {

  default void appendMetadataToFollowUps(final Consumer<RecordMetadata> decorator) {}

  default void appendOperationReferenceToFollowUps(final long operationReference) {
    if (operationReference != operationReferenceNullValue()) {
      appendMetadataToFollowUps(
          metadata -> {
            // `operationReference` from `metadata` should have a higher precedence
            if (metadata.getOperationReference() == operationReferenceNullValue()) {
              metadata.operationReference(operationReference);
            }
          });
    }
  }

  default void appendBatchOperationReferenceToFollowUps(final long batchOperationReference) {
    if (batchOperationReference != batchOperationReferenceNullValue()) {
      appendMetadataToFollowUps(
          metadata -> metadata.batchOperationReference(batchOperationReference));
    }
  }

  /**
   * Marks the follow-ups as part of the same internal operation. A follow-up that already names an
   * internal operation keeps it.
   */
  default void appendInternalOperationReferenceToFollowUps(
      final InternalOperationReference internalOperationReference) {
    if (internalOperationReference.isPresent()) {
      appendMetadataToFollowUps(
          metadata -> {
            if (!metadata.getInternalOperation().isPresent()) {
              metadata.internalOperation(internalOperationReference);
            }
          });
    }
  }

  default void appendAuthInfoToFollowUps(final @Nullable AuthInfo authInfo) {
    if (authInfo != null && authInfo.hasAnyClaims()) {
      // Create it once, so that all follow-ups of this command share one encoding.
      final var actorAuthInfo = authInfo.withActorClaimsOnly();
      appendMetadataToFollowUps(
          metadata -> metadata.authorization(needsFullClaims(metadata) ? authInfo : actorAuthInfo));
    }
  }

  /**
   * The engine checks the claims of a batch operation command. For other follow-ups, only the actor
   * has a reader: the engine does not check the claims of internal commands.
   */
  private static boolean needsFullClaims(final RecordMetadata metadata) {
    return metadata.getRecordType() == RecordType.COMMAND
        && metadata.getBatchOperationReference() != batchOperationReferenceNullValue();
  }

  default void appendAgentInfoToFollowUps(final @Nullable Agent agentInfo) {
    if (agentInfo != null) {
      // make a copy to rule out any side effects
      final var agent = AgentInfo.of(agentInfo);
      appendMetadataToFollowUps(metadata -> metadata.agent(agent));
    }
  }

  default void appendRequestSourceToFollowUps(
      final @Nullable ChannelType channelType, final @Nullable String toolName) {
    if (channelType != null) {
      appendMetadataToFollowUps(metadata -> metadata.requestChannelType(channelType));
    }
    if (toolName != null && !toolName.isEmpty()) {
      appendMetadataToFollowUps(metadata -> metadata.requestToolName(toolName));
    }
  }
}

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
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.Agent;
import java.util.function.Consumer;

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

  default void appendAuthInfoToFollowUps(final AuthInfo authInfo) {
    if (authInfo != null && authInfo.hasAnyClaims()) {
      // make a copy to rule out any side effects
      final var authorization = AuthInfo.of(authInfo);
      appendMetadataToFollowUps(metadata -> metadata.authorization(authorization));
    }
  }

  default void appendAgentInfoToFollowUps(final Agent agentInfo) {
    if (agentInfo != null) {
      // make a copy to rule out any side effects
      final var agent = AgentInfo.of(agentInfo);
      appendMetadataToFollowUps(metadata -> metadata.agent(agent));
    }
  }
}

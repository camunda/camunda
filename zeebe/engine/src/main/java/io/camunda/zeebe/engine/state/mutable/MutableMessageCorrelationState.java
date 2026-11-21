/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.MessageCorrelationState;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;

public interface MutableMessageCorrelationState
    extends MessageCorrelationState, StreamProcessorLifecycleAware {

  void removeMessageCorrelation(long messageKey);

  void putMessageCorrelation(long messageKey, long requestId, int requestStreamId);

  /**
   * Persists the request data of a message correlation, including the operation reference of the
   * command that initiated the correlation, so the terminal CORRELATED/NOT_CORRELATED records can
   * be correlated back to the client's operation.
   */
  void putMessageCorrelation(
      long messageKey, long requestId, int requestStreamId, long operationReference);
}

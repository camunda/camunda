/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.logstreams;

import io.camunda.eventbridge.broker.publish.EventStreamAppender;

/** Callback for batch completion events from the {@link EventStreamAppender}. */
public interface EventStreamListener {

  void onCommitted(long requestId, long firstPosition, long lastPosition);

  void onFailed(long requestId, Throwable error);
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.processing;

/**
 * Receives the bucket writes the processor decided on. Implemented by the publisher on the leader.
 *
 * <p>Called by the processor on the stream processor thread, after the event that caused the
 * request is committed and only while processing, never while replaying. Implementations must hand
 * the request over to their own actor and return quickly.
 */
public interface LogArchivePublication {

  void publish(PublishRequest request);
}

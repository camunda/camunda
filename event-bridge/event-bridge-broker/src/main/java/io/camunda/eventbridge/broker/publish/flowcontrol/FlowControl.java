/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.publish.flowcontrol;

/** Admission control for the inbound publish path. Must be thread-safe. */
public interface FlowControl {

  boolean tryAcquire(int entryCount, int bytesLength);

  void onCompleted(int entryCount, int bytesLength);
}

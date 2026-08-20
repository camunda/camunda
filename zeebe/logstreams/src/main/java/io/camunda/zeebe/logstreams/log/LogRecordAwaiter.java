/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.log;

/** Listener invoked by {@link LogStream} when new records are available to read. */
@FunctionalInterface
public interface LogRecordAwaiter {

  /** Will be invoked when new records are available in LogStream. */
  void onRecordAvailable();
}

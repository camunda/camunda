/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.consumer;

import io.camunda.zeebe.eventplane.segment.LogId;
import io.camunda.zeebe.protocol.record.Record;

/**
 * Takes the records of the logs a reader or a worker reads. Each log's records come in position
 * order, on one thread at a time, but a worker's logs come on threads of their own, so a handler
 * that a worker shares must be thread-safe. Delivery is at least once: a record can come again,
 * after a handover or a restart.
 */
@FunctionalInterface
public interface RecordHandler {

  /**
   * Takes one record. If it throws, the reader delivers the record again on a later poll.
   *
   * @param log the log the record belongs to
   * @param record the record, which the handler may keep
   */
  void onRecord(LogId log, Record<?> record);
}

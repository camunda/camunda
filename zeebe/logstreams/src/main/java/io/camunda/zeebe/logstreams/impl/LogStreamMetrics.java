/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl;

import io.camunda.zeebe.logstreams.impl.flowcontrol.FlowControl.Rejection;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.util.CloseableSilently;

public interface LogStreamMetrics {

  void increaseInflightAppends();

  void decreaseInflightAppends();

  void setInflightRequests(int count);

  void setRequestLimit(int limit);

  void increaseInflightRequests();

  void decreaseInflightRequests();

  CloseableSilently startWriteTimer();

  CloseableSilently startCommitTimer();

  void setLastWrittenPosition(long position);

  void setLastCommittedPosition(long position);

  void recordAppendedEntry(int amount, RecordType recordType, ValueType valueType, Intent intent);

  void flowControlAccepted(WriteContext context, int size);

  void flowControlRejected(WriteContext context, int size, Rejection reason);

  void setExportingRate(long value);

  void setWriteRateMaxLimit(long value);

  void setPartitionLoad(double load);

  void setWriteRateLimit(double value);
}

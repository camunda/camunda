/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.VersionInfo;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * A record read from the log that this broker cannot process: its value type is unknown, or no
 * registered processor accepts it. The state machines yield it to the {@link StreamProcessor}
 * instead of failing, so that it can decide how to react.
 *
 * <p>The fields are copied because the event and the metadata they are read from are reused.
 *
 * @param valueType {@link ValueType#NULL_VAL} or {@link ValueType#SBE_UNKNOWN} if this broker does
 *     not know it
 */
record UnprocessableRecord(
    long position, RecordType recordType, ValueType valueType, VersionInfo brokerVersion) {

  static UnprocessableRecord of(final LoggedEvent event, final RecordMetadata metadata) {
    return new UnprocessableRecord(
        event.getPosition(),
        metadata.getRecordType(),
        metadata.getValueType(),
        metadata.getBrokerVersion());
  }

  static UnprocessableRecord of(final TypedRecord<?> record) {
    return new UnprocessableRecord(
        record.getPosition(),
        record.getRecordType(),
        record.getValueType(),
        VersionInfo.parse(record.getBrokerVersion()));
  }

  /**
   * Whether the record was written by a broker version newer than this one. That is expected while
   * a rolling update is in progress and resolves once this broker is upgraded; any other record
   * that this broker cannot process is a genuine error.
   */
  boolean isFromNewerVersion() {
    final var current = RecordMetadata.CURRENT_BROKER_VERSION;
    if (brokerVersion.getMajorVersion() != current.getMajorVersion()) {
      return brokerVersion.getMajorVersion() > current.getMajorVersion();
    }
    if (brokerVersion.getMinorVersion() != current.getMinorVersion()) {
      return brokerVersion.getMinorVersion() > current.getMinorVersion();
    }
    return brokerVersion.getPatchVersion() > current.getPatchVersion();
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.test.util.stream.StreamWrapper;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

public abstract class ExporterRecordStream<
        T extends RecordValue, S extends ExporterRecordStream<T, S>>
    extends StreamWrapper<Record<T>, S> {

  public ExporterRecordStream(final Stream<Record<T>> wrappedStream) {
    super(wrappedStream);
  }

  public S valueFilter(final Predicate<T> predicate) {
    return filter(r -> predicate.test(r.getValue()));
  }

  public S onlyCommands() {
    return filter(m -> m.getRecordType() == RecordType.COMMAND);
  }

  public S onlyCommandRejections() {
    return filter(m -> m.getRecordType() == RecordType.COMMAND_REJECTION);
  }

  public S onlyEvents() {
    return filter(m -> m.getRecordType() == RecordType.EVENT);
  }

  public S withPosition(final long position) {
    return filter(r -> r.getPosition() == position);
  }

  public S withSourceRecordPosition(final long sourceRecordPosition) {
    return filter(r -> r.getSourceRecordPosition() == sourceRecordPosition);
  }

  public S withRecordKey(final long key) {
    return filter(r -> r.getKey() == key);
  }

  public S withTimestamp(final long timestamp) {
    return filter(r -> r.getTimestamp() == timestamp);
  }

  public S withIntents(final Intent... intents) {
    final var intentsList = Arrays.asList(intents);
    return filter(m -> intentsList.contains(m.getIntent()));
  }

  public S withIntent(final Intent intent) {
    return filter(m -> m.getIntent() == intent);
  }

  public S withPartitionId(final int partitionId) {
    return filter(m -> m.getPartitionId() == partitionId);
  }

  public S withRecordType(final RecordType recordType) {
    return filter(m -> m.getRecordType() == recordType);
  }

  public S withRejectionType(final RejectionType rejectionType) {
    return filter(m -> m.getRejectionType() == rejectionType);
  }

  public S withRejectionReason(final String rejectionReason) {
    return filter(m -> rejectionReason.equals(m.getRejectionReason()));
  }

  public S withValueType(final ValueType valueType) {
    return filter(m -> m.getValueType() == valueType);
  }

  public S withValueTypes(final ValueType... valueTypes) {
    final var valueTypesSet = Set.of(valueTypes);
    return filter(m -> valueTypesSet.contains(m.getValueType()));
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import java.util.stream.Stream;

public class GroupRecordStream extends ExporterRecordStream<GroupRecordValue, GroupRecordStream> {

  public GroupRecordStream(final Stream<Record<GroupRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected GroupRecordStream supply(final Stream<Record<GroupRecordValue>> wrappedStream) {
    return new GroupRecordStream(wrappedStream);
  }

  public GroupRecordStream withGroupKey(final long groupKey) {
    return valueFilter(v -> v.getGroupKey() == groupKey);
  }

  public GroupRecordStream withName(final String name) {
    return valueFilter(v -> v.getName().equals(name));
  }

  public GroupRecordStream withEntityKey(final String entityKey) {
    return valueFilter(v -> v.getEntityKey().equals(entityKey));
  }

  public GroupRecordStream withEntityType(final EntityType entityType) {
    return valueFilter(v -> v.getEntityType() == entityType);
  }
}

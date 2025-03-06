/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import java.util.stream.Stream;

public class RoleRecordStream extends ExporterRecordStream<RoleRecordValue, RoleRecordStream> {

  public RoleRecordStream(final Stream<Record<RoleRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected RoleRecordStream supply(final Stream<Record<RoleRecordValue>> wrappedStream) {
    return new RoleRecordStream(wrappedStream);
  }

  public RoleRecordStream withRoleKey(final long roleKey) {
    return valueFilter(v -> v.getRoleKey() == roleKey);
  }

  public RoleRecordStream withName(final String name) {
    return valueFilter(v -> v.getName().equals(name));
  }

  public RoleRecordStream withEntityKey(final String entityKey) {
    return valueFilter(v -> v.getEntityKey().equals(entityKey));
  }
}

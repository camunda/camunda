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
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import java.util.stream.Stream;

public class TenantRecordStream
    extends ExporterRecordStream<TenantRecordValue, TenantRecordStream> {

  public TenantRecordStream(final Stream<Record<TenantRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected TenantRecordStream supply(final Stream<Record<TenantRecordValue>> wrappedStream) {
    return new TenantRecordStream(wrappedStream);
  }

  public TenantRecordStream withTenantKey(final long tenantKey) {
    return valueFilter(v -> v.getTenantKey() == tenantKey);
  }

  public TenantRecordStream withTenantId(final String tenantId) {
    return valueFilter(v -> v.getTenantId().equals(tenantId));
  }

  public TenantRecordStream withName(final String name) {
    return valueFilter(v -> v.getName().equals(name));
  }

  public TenantRecordStream withEntityId(final String entityId) {
    return valueFilter(v -> v.getEntityId().equals(entityId));
  }

  public TenantRecordStream withEntityType(final EntityType entityType) {
    return valueFilter(v -> v.getEntityType() == entityType);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.SecretReferenceRecordValue;
import java.util.stream.Stream;

public final class SecretReferenceRecordStream
    extends ExporterRecordStream<SecretReferenceRecordValue, SecretReferenceRecordStream> {

  public SecretReferenceRecordStream(
      final Stream<Record<SecretReferenceRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected SecretReferenceRecordStream supply(
      final Stream<Record<SecretReferenceRecordValue>> wrappedStream) {
    return new SecretReferenceRecordStream(wrappedStream);
  }

  public SecretReferenceRecordStream withStoreId(final String storeId) {
    return valueFilter(v -> storeId.equals(v.getStoreId()));
  }

  public SecretReferenceRecordStream withSecretReference(final String secretReference) {
    return valueFilter(v -> secretReference.equals(v.getSecretReference()));
  }
}

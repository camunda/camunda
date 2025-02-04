/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import java.util.stream.Stream;

public class AuthorizationRecordStream
    extends ExporterRecordStream<AuthorizationRecordValue, AuthorizationRecordStream> {

  public AuthorizationRecordStream(final Stream<Record<AuthorizationRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected AuthorizationRecordStream supply(
      final Stream<Record<AuthorizationRecordValue>> wrappedStream) {
    return new AuthorizationRecordStream(wrappedStream);
  }

  public AuthorizationRecordStream withOwnerKey(final long ownerKey) {
    return valueFilter(v -> v.getOwnerKey() == ownerKey);
  }

  public AuthorizationRecordStream withOwnerId(final String ownerId) {
    return valueFilter(v -> v.getOwnerId().equals(ownerId));
  }

  public AuthorizationRecordStream withAuthorizationKey(final long authorizationKey) {
    return valueFilter(v -> v.getAuthorizationKey() == authorizationKey);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import java.util.stream.Stream;

public class UserRecordStream extends ExporterRecordStream<UserRecordValue, UserRecordStream> {

  public UserRecordStream(final Stream<Record<UserRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected UserRecordStream supply(final Stream<Record<UserRecordValue>> wrappedStream) {
    return new UserRecordStream(wrappedStream);
  }

  public UserRecordStream withUsername(final String username) {
    return valueFilter(v -> v.getUsername().equals(username));
  }

  public UserRecordStream withUserKey(final long userKey) {
    return valueFilter(v -> v.getUserKey() == userKey);
  }
}

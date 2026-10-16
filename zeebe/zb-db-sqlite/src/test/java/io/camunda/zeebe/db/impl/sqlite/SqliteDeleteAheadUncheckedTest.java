/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.sqlite;

import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.conformance.ConformanceDbTestBase;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import org.junit.jupiter.api.Test;

/** Without preconditions the delete-ahead guard costs nothing, so it does not check. */
final class SqliteDeleteAheadUncheckedTest extends ConformanceDbTestBase {

  @Override
  protected <T extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
      ZeebeDbFactory<T> factory() {
    return new SqliteDbFactory<>();
  }

  @Test
  void shouldNotCheckDeletesAheadOfCursorByDefault() {
    // given
    final var cf = longColumnFamily(Cf.ONE);
    for (long i = 0; i < 10; i++) {
      cf.insert(dbLong(i), dbLong(i));
    }

    // then
    assertThatCode(() -> cf.forEach((key, value) -> cf.deleteIfExists(dbLong(9))))
        .doesNotThrowAnyException();
  }
}

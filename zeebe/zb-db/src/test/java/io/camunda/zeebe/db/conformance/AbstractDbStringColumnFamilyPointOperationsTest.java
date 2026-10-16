/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.impl.DbString;
import org.junit.Test;

/**
 * Point-operation (insert, update, get, delete, count) conformance tests for a column family keyed
 * by a string. A backend without iteration support can run this class on its own.
 */
public abstract class AbstractDbStringColumnFamilyPointOperationsTest
    extends AbstractDbStringColumnFamilyTestBase {

  @Test
  public void shouldUpsertValue() {
    // given
    key.wrapString("foo");
    value.wrapString("baring");

    // when
    columnFamily.upsert(key, value);
    value.wrapString("yes");

    // then
    final DbString zbLong = columnFamily.get(key);

    assertThat(zbLong).isNotNull();
    assertThat(zbLong.toString()).isEqualTo("baring");

    // zbLong and value are referencing the same object
    assertThat(value.toString()).isEqualTo("baring");
  }
}

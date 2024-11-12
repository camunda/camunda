/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import io.camunda.zeebe.protocol.EnumValue;
import org.junit.jupiter.api.Test;

final class ForeignKeyTest {

  @Test
  void shouldCollectSingleForeignKey() {
    // given
    final var key = new DbForeignKey<>(mock(DbKey.class), TestColumnFamilies.TEST_COLUMN_FAMILY);

    // then
    assertThat(key.containedForeignKeys()).singleElement().isEqualTo(key);
  }

  @Test
  void shouldCollectForeignKeysFromSingleComposite() {
    // given
    final var key = new DbForeignKey<>(mock(DbKey.class), TestColumnFamilies.TEST_COLUMN_FAMILY);
    final var composite = new DbCompositeKey<>(key, mock(DbKey.class));

    // then
    assertThat(composite.containedForeignKeys()).singleElement().isEqualTo(key);
  }

  @Test
  void shouldCollectForeignKeysFromNestedComposite() {
    // given
    final var key1 = new DbForeignKey<>(mock(DbKey.class), TestColumnFamilies.TEST_COLUMN_FAMILY);
    final var key2 = new DbForeignKey<>(mock(DbKey.class), TestColumnFamilies.TEST_COLUMN_FAMILY);
    final var key3 = new DbForeignKey<>(mock(DbKey.class), TestColumnFamilies.TEST_COLUMN_FAMILY);
    final var composite = new DbCompositeKey<>(new DbCompositeKey<>(key1, key2), key3);

    // then
    assertThat(composite.containedForeignKeys()).containsExactly(key1, key2, key3);
  }

  @Test
  void shouldCollectForeignKeysFromTenantAwareKey() {
    // given
    final var foreignKey =
        new DbForeignKey<>(mock(DbKey.class), TestColumnFamilies.TEST_COLUMN_FAMILY);
    final var tenantAwareKey =
        new DbTenantAwareKey<>(mock(DbString.class), foreignKey, PlacementType.SUFFIX);

    // then
    assertThat(tenantAwareKey.containedForeignKeys()).singleElement().isEqualTo(foreignKey);
  }

  @Test
  void shouldCollectForeignKeysFromTenantAwareComposite() {
    // given
    final var key1 = new DbForeignKey<>(mock(DbKey.class), TestColumnFamilies.TEST_COLUMN_FAMILY);
    final var key2 = new DbForeignKey<>(mock(DbKey.class), TestColumnFamilies.TEST_COLUMN_FAMILY);
    final var compositeKey = new DbCompositeKey<>(key1, key2);
    final var tenantAwareKey =
        new DbTenantAwareKey<>(mock(DbString.class), compositeKey, PlacementType.SUFFIX);

    // then
    assertThat(tenantAwareKey.containedForeignKeys()).containsExactly(key1, key2);
  }

  private enum TestColumnFamilies implements EnumValue {
    TEST_COLUMN_FAMILY(0);

    private final int value;

    TestColumnFamilies(final int value) {
      this.value = value;
    }

    @Override
    public int getValue() {
      return value;
    }
  }
}

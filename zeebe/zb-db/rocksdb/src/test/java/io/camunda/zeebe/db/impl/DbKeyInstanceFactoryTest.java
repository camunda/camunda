/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.impl.DbForeignKey.MatchType;
import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.junit.jupiter.api.Test;

final class DbKeyInstanceFactoryTest {

  @Test
  void shouldCreateBlankInstanceOfSimpleKey() {
    // given
    final var template = new DbLong();
    template.wrapLong(42);

    // when
    final var instance = DbKeyInstanceFactory.newInstance(template);

    // then
    assertThat(instance).isNotSameAs(template);
    assertThat(instance.getValue()).isZero();
    assertThat(template.getValue()).isEqualTo(42);
  }

  @Test
  void shouldCreateCompositeKeyWithNewParts() {
    // given
    final var first = new DbString();
    first.wrapString("tenant");
    final var second = new DbLong();
    second.wrapLong(7);
    final var template = new DbCompositeKey<>(first, second);

    // when
    final var instance = DbKeyInstanceFactory.newInstance(template);

    // then
    assertThat(instance.first()).isInstanceOf(DbString.class).isNotSameAs(first);
    assertThat(instance.second()).isInstanceOf(DbLong.class).isNotSameAs(second);
    assertThat(instance.second().getValue()).isZero();
  }

  @Test
  void shouldKeepForeignKeyTarget() {
    // given
    final Predicate<DbLong> skip = key -> key.getValue() < 0;
    final var template =
        new DbForeignKey<>(new DbLong(), DefaultColumnFamily.DEFAULT, MatchType.Prefix, skip);

    // when
    final var instance = DbKeyInstanceFactory.newInstance(template);

    // then
    assertThat(instance.inner()).isInstanceOf(DbLong.class).isNotSameAs(template.inner());
    assertThat(instance.columnFamily()).isEqualTo(DefaultColumnFamily.DEFAULT);
    assertThat(instance.match()).isEqualTo(MatchType.Prefix);
    assertThat(instance.skip()).isSameAs(skip);
  }

  @Test
  void shouldKeepTenantAwarePlacement() {
    // given
    final var template = new DbTenantAwareKey<>(new DbString(), new DbLong(), PlacementType.SUFFIX);

    // when
    final var instance = DbKeyInstanceFactory.newInstance(template);

    // then
    assertThat(instance.tenantKey()).isNotSameAs(template.tenantKey());
    assertThat(instance.wrappedKey()).isInstanceOf(DbLong.class).isNotSameAs(template.wrappedKey());
    assertThat(instance.placementType()).isEqualTo(PlacementType.SUFFIX);
  }

  @Test
  void shouldReuseStatelessNullKey() {
    // when
    final var instance = DbKeyInstanceFactory.newInstance(new DbNullKey());

    // then
    assertThat(instance).isSameAs(DbNullKey.INSTANCE);
  }

  @Test
  void shouldCreateEnumValueOfTheSameEnumType() {
    // given
    final var template = new DbEnumValue<>(MatchType.class);
    template.setValue(MatchType.Prefix);

    // when
    final var instance = DbKeyInstanceFactory.newInstance(template);

    // then
    assertThat(instance).isNotSameAs(template);
    assertThat(instance.getEnumType()).isEqualTo(MatchType.class);
    instance.setValue(MatchType.Full);
    assertThat(instance.getValue()).isEqualTo(MatchType.Full);
    assertThat(template.getValue()).isEqualTo(MatchType.Prefix);
  }

  @Test
  void shouldCreateOtherKeysThroughTheirNoArgConstructor() {
    // when
    final var instance = DbKeyInstanceFactory.newInstance(new KeyWithNoArgConstructor());

    // then
    assertThat(instance).isInstanceOf(KeyWithNoArgConstructor.class);
  }

  @Test
  void shouldRejectKeyWithoutNoArgConstructor() {
    // given
    final var template = new KeyWithoutNoArgConstructor(1);

    // when - then
    assertThatThrownBy(() -> DbKeyInstanceFactory.newInstance(template))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining(KeyWithoutNoArgConstructor.class.getName());
  }

  private static class KeyWithNoArgConstructor implements DbKey {

    private KeyWithNoArgConstructor() {}

    @Override
    public void wrap(final DirectBuffer buffer, final int offset, final int length) {}

    @Override
    public int getLength() {
      return 0;
    }

    @Override
    public int write(final MutableDirectBuffer buffer, final int offset) {
      return 0;
    }
  }

  private static final class KeyWithoutNoArgConstructor extends KeyWithNoArgConstructor {

    private KeyWithoutNoArgConstructor(final int ignored) {}
  }
}

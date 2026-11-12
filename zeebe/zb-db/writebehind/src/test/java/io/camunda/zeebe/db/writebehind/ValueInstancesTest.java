/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbEnumValue;
import io.camunda.zeebe.db.impl.DbForeignKey.MatchType;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.junit.jupiter.api.Test;

final class ValueInstancesTest {

  @Test
  void shouldShareStatelessNil() {
    // when
    final var instance = ValueInstances.newInstance(DbNil.INSTANCE);

    // then
    assertThat(instance).isSameAs(DbNil.INSTANCE);
  }

  @Test
  void shouldCreateNewInstanceOfValueClassEachTime() {
    // given
    final var template = TestRecord.of("job", 3);

    // when
    final var first = ValueInstances.newInstance(template);
    final var second = ValueInstances.newInstance(template);

    // then
    assertThat(first).isInstanceOf(TestRecord.class).isNotSameAs(template).isNotSameAs(second);
    assertThat(((TestRecord) first).name()).isEmpty();
  }

  @Test
  void shouldCreateValueClassThroughPrivateConstructor() {
    // when
    final var instance = ValueInstances.newInstance(PrivateValue.create());

    // then
    assertThat(instance).isInstanceOf(PrivateValue.class);
  }

  @Test
  void shouldCreateKeyValueWithNewParts() {
    // given
    final var first = new DbString();
    first.wrapString("tenant");
    final var second = new DbLong();
    second.wrapLong(7);
    final var template = new DbCompositeKey<>(first, second);

    // when
    final var instance = ValueInstances.newInstance(template);

    // then
    assertThat(instance).isInstanceOf(DbCompositeKey.class).isNotSameAs(template);
    final var composite = (DbCompositeKey<?, ?>) instance;
    assertThat(composite.first()).isInstanceOf(DbString.class).isNotSameAs(first);
    assertThat(composite.second()).isInstanceOf(DbLong.class).isNotSameAs(second);
  }

  @Test
  void shouldCreateEnumValueOfTheSameEnumType() {
    // given
    final var template = new DbEnumValue<>(MatchType.class);

    // when
    final var instance = ValueInstances.newInstance(template);

    // then
    assertThat(instance).isInstanceOf(DbEnumValue.class).isNotSameAs(template);
    assertThat(((DbEnumValue<?>) instance).getEnumType()).isEqualTo(MatchType.class);
  }

  @Test
  void shouldRejectValueClassWithoutNoArgConstructor() {
    // given
    final var template = new ValueWithoutNoArgConstructor(1);

    // when - then
    assertThatThrownBy(() -> ValueInstances.newInstance(template))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(ValueWithoutNoArgConstructor.class.getName());
  }

  private static final class PrivateValue extends EmptyValue {

    private PrivateValue() {}

    static PrivateValue create() {
      return new PrivateValue();
    }
  }

  private static final class ValueWithoutNoArgConstructor extends EmptyValue {

    private ValueWithoutNoArgConstructor(final int ignored) {}
  }

  private abstract static class EmptyValue implements DbValue {

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
}

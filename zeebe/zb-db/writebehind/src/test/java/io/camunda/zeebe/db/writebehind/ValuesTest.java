/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbEnumValue;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbForeignKey.MatchType;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import org.agrona.ExpandableArrayBuffer;
import org.junit.jupiter.api.Test;

final class ValuesTest {

  private final ExpandableArrayBuffer scratch = new ExpandableArrayBuffer();

  @Test
  void shouldHoldExactlyTheEncodedBytes() {
    // given
    final var value = TestRecord.of("job", 3);

    // when
    final var held = Values.hold(value, scratch);

    // then
    assertThat(held.bytes()).hasSize(value.getLength());
  }

  @Test
  void shouldNotSeeLaterChangesOfTheCallersValue() {
    // given
    final var value = TestRecord.of("job", 3);
    final var held = Values.hold(value, scratch);

    // when
    value.name("changed").counter(4);

    // then
    final var copy = new TestRecord();
    Values.copyInto(held, copy);
    assertThat(copy.name()).isEqualTo("job");
    assertThat(copy.counter()).isEqualTo(3);
  }

  @Test
  void shouldKeepCopiedValueIntactWhenTheScratchBufferIsReused() {
    // given
    final var first = Values.hold(TestRecord.of("first", 1), scratch);
    final var copy = new TestRecord();
    Values.copyInto(first, copy);

    // when
    Values.hold(TestRecord.of("second", 2), scratch);

    // then
    assertThat(copy.name()).isEqualTo("first");
  }

  @Test
  void shouldNotChangeHeldValueWhenTheCopyChanges() {
    // given
    final var held = Values.hold(TestRecord.of("job", 3), scratch);
    final var copy = new TestRecord();
    Values.copyInto(held, copy);

    // when
    copy.name("changed").counter(4);

    // then
    final var again = new TestRecord();
    Values.copyInto(held, again);
    assertThat(again.name()).isEqualTo("job");
    assertThat(again.counter()).isEqualTo(3);
  }

  @Test
  void shouldCopyIntoAnotherValueClass() {
    // given
    final var held = Values.hold(TestRecord.of("job", 3), scratch);
    final var target = new TestRecord.NameOnly();

    // when
    Values.copyInto(held, target);

    // then
    assertThat(target.name()).isEqualTo("job");
  }

  @Test
  void shouldHoldNil() {
    // when
    final var held = Values.hold(DbNil.INSTANCE, scratch);

    // then
    assertThat(held.bytes()).hasSize(DbNil.INSTANCE.getLength());
  }

  @Test
  void shouldHoldCompositeKeyValue() {
    // given
    final var first = new DbString();
    first.wrapString("tenant");
    final var second = new DbLong();
    second.wrapLong(7);
    final var value = new DbCompositeKey<>(first, second);

    // when
    final var held = Values.hold(value, scratch);
    second.wrapLong(8);

    // then
    final var copy = new DbCompositeKey<>(new DbString(), new DbLong());
    Values.copyInto(held, copy);
    assertThat(copy.first().toString()).isEqualTo("tenant");
    assertThat(copy.second().getValue()).isEqualTo(7);
  }

  @Test
  void shouldHoldForeignKeyValueWithItsTarget() {
    // given
    final var inner = new DbLong();
    inner.wrapLong(7);
    final var value = new DbForeignKey<>(inner, TestColumnFamily.TARGET, MatchType.Full);

    // when
    final var held = Values.hold(value, scratch);

    // then
    final var copy = new DbForeignKey<>(new DbLong(), TestColumnFamily.TARGET, MatchType.Full);
    Values.copyInto(held, copy);
    assertThat(copy.inner().getValue()).isEqualTo(7);
  }

  @Test
  void shouldHoldEnumValue() {
    // given
    final var value = new DbEnumValue<>(MatchType.class);
    value.setValue(MatchType.Prefix);

    // when
    final var held = Values.hold(value, scratch);
    value.setValue(MatchType.Full);

    // then
    final var copy = new DbEnumValue<>(MatchType.class);
    Values.copyInto(held, copy);
    assertThat(copy.getValue()).isEqualTo(MatchType.Prefix);
  }
}

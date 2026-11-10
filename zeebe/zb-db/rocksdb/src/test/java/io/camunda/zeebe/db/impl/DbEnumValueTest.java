/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.agrona.ExpandableArrayBuffer;
import org.junit.Test;

public final class DbEnumValueTest {

  @Test
  public void shouldSetAndGetValue() {
    // given
    final DbEnumValue<TestEnum> dbEnumValue = new DbEnumValue<>(TestEnum.class);

    // when
    dbEnumValue.setValue(TestEnum.SECOND);

    // then
    assertThat(dbEnumValue.getValue()).isEqualTo(TestEnum.SECOND);
  }

  @Test
  public void shouldWrapAndWrite() {
    // given
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    final DbEnumValue<TestEnum> dbEnumValue = new DbEnumValue<>(TestEnum.class);
    dbEnumValue.setValue(TestEnum.THIRD);
    dbEnumValue.write(buffer, 0);

    // when
    final var readEnumValue = new DbEnumValue<TestEnum>(TestEnum.class);
    readEnumValue.wrap(buffer, 0, dbEnumValue.getLength());

    // then
    assertThat(readEnumValue.getValue()).isEqualTo(TestEnum.THIRD);
  }

  private enum TestEnum {
    FIRST,
    SECOND,
    THIRD
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util;

import static io.zeebe.util.ByteUnit.BYTES;
import static io.zeebe.util.ByteUnit.GIGABYTES;
import static io.zeebe.util.ByteUnit.KILOBYTES;
import static io.zeebe.util.ByteUnit.MEGABYTES;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public final class ByteValueTest {

  @Test
  public void shouldConvertUnitBytes() {
    final long byteValue = 1_000_000_000L;
    assertThat(new ByteValue(byteValue, BYTES).toBytes()).isEqualTo(byteValue);
  }

  @Test
  public void shouldConvertUnitKilobytesInConstructor() {
    final long kiloByteValue = 1_000_000L;
    assertThat(new ByteValue(kiloByteValue, KILOBYTES).toBytes()).isEqualTo(kiloByteValue * 1024);
  }

  @Test
  public void shouldConvertUnitKilobytesInStaticMethod() {
    // given
    final long kiloByteValue = 5;

    // when
    final long actual = ByteValue.ofKilobytes(kiloByteValue);

    // then
    assertThat(actual).isEqualTo(kiloByteValue * 1024);
  }

  @Test
  public void shouldConvertUnitMegabytes() {
    final long megaByteValue = 1_000L;
    assertThat(new ByteValue(megaByteValue, MEGABYTES).toBytes())
        .isEqualTo(megaByteValue * (1024 * 1024));
  }

  @Test
  public void shouldConvertUnitMegabytesInStaticMethod() {
    // given
    final long megaByteValue = 7;

    // when
    final long actual = ByteValue.ofMegabytes(megaByteValue);

    // then
    assertThat(actual).isEqualTo(megaByteValue * 1024 * 1024);
  }

  @Test
  public void shouldConvertUnitGigabytes() {
    final long gigaBytes = 100L;
    assertThat(new ByteValue(gigaBytes, GIGABYTES).toBytes())
        .isEqualTo(gigaBytes * (1024 * 1024 * 1024));
  }

  @Test
  public void shouldConvertUnitGigabytesInStaticMethod() {
    // given
    final long gigaByteValue = 5;

    // when
    final long actual = ByteValue.ofGigabytes(gigaByteValue);

    // then
    assertThat(actual).isEqualTo(gigaByteValue * 1024 * 1024 * 1024);
  }
}

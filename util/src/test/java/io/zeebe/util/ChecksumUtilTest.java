/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class ChecksumUtilTest {

  private Path thirdExampleFile;
  private Path exampleFile;
  private Path otherExampleFile;

  @Before
  public void setup() throws Exception {
    exampleFile = Path.of(ChecksumUtilTest.class.getResource("/exampleFile.txt").toURI());
    otherExampleFile = Path.of(ChecksumUtilTest.class.getResource("/otherExampleFile.txt").toURI());
    thirdExampleFile = Path.of(ChecksumUtilTest.class.getResource("/thirdExampleFile.txt").toURI());
  }

  @Test
  public void shouldGenerateTheSameChecksumForOneFile() throws Exception {
    // given
    final var expectedChecksum = ChecksumUtil.createCombinedChecksum(List.of(exampleFile));

    // when
    final var actual = ChecksumUtil.createCombinedChecksum(List.of(exampleFile));

    // then
    assertThat(actual).isEqualTo(expectedChecksum);
  }

  @Test
  public void shouldGenerateTheSameChecksumForMultipleFiles() throws Exception {
    // given
    final var expectedChecksum =
        ChecksumUtil.createCombinedChecksum(
            List.of(exampleFile, otherExampleFile, thirdExampleFile));

    // when
    final var actual =
        ChecksumUtil.createCombinedChecksum(
            List.of(exampleFile, otherExampleFile, thirdExampleFile));

    // then
    assertThat(actual).isEqualTo(expectedChecksum);
  }

  @Test
  public void shouldGenerateDifferentChecksumForDifferentFiles() throws Exception {
    // given
    final var expectedChecksum = ChecksumUtil.createCombinedChecksum(List.of(exampleFile));

    // when
    final var actual = ChecksumUtil.createCombinedChecksum(List.of(thirdExampleFile));

    // then
    assertThat(actual).isNotEqualTo(expectedChecksum);
  }

  @Test
  public void shouldGenerateDifferentChecksumForDifferentCombination() throws Exception {
    // given
    final var expectedChecksum =
        ChecksumUtil.createCombinedChecksum(List.of(exampleFile, otherExampleFile));

    // when
    final var actual = ChecksumUtil.createCombinedChecksum(List.of(exampleFile, thirdExampleFile));

    // then
    assertThat(actual).isNotEqualTo(expectedChecksum);
  }

  @Test
  public void shouldGenerateDifferentChecksumOnDifferentOrder() throws Exception {
    // given
    final var expectedChecksum =
        ChecksumUtil.createCombinedChecksum(
            List.of(exampleFile, otherExampleFile, thirdExampleFile));

    // when
    final var actual =
        ChecksumUtil.createCombinedChecksum(
            List.of(thirdExampleFile, otherExampleFile, exampleFile));

    // then
    assertThat(actual).isNotEqualTo(expectedChecksum);
  }
}

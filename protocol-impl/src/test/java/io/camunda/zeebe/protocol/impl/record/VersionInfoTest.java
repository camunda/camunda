/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.record;

import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class VersionInfoTest {
  @ParameterizedTest
  @MethodSource("versionInfoArguments")
  public void testCompareVersionInfos(
      final VersionInfo v1, final VersionInfo v2, final int expected) {
    final int actual = v1.compareTo(v2);
    Assertions.assertEquals(expected, actual);
  }

  static Stream<Arguments> versionInfoArguments() {
    return Stream.of(
        arguments(new VersionInfo(1, 2, 3), new VersionInfo(1, 2, 3), 0),
        arguments(new VersionInfo(1, 2, 3), new VersionInfo(1, 2, 4), -1),
        arguments(new VersionInfo(1, 2, 3), new VersionInfo(1, 3, 0), -1),
        arguments(new VersionInfo(1, 2, 3), new VersionInfo(2, 0, 0), -1),
        arguments(new VersionInfo(1, 2, 3), new VersionInfo(0, 0, 0), 1),
        arguments(new VersionInfo(1, 0, 0), new VersionInfo(0, 1, 0), 1),
        arguments(new VersionInfo(0, 1, 0), new VersionInfo(0, 0, 1), 1),
        arguments(new VersionInfo(0, 0, 1), new VersionInfo(1, 0, 0), -1),
        arguments(new VersionInfo(0, 0, 1), new VersionInfo(0, 1, 0), -1),
        arguments(new VersionInfo(0, 1, 0), new VersionInfo(1, 0, 0), -1));
  }
}

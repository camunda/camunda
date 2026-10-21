/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.socket;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

final class SocketUtilTest {

  @Test
  void shouldHandOutUniqueAddressesToConcurrentCallers() {
    // given
    final int callers = 200;

    // when
    final List<Integer> ports =
        IntStream.range(0, callers)
            .parallel()
            .mapToObj(i -> SocketUtil.getNextAddress().getPort())
            .toList();

    // then
    assertThat(ports).doesNotHaveDuplicates().hasSize(callers);
  }
}

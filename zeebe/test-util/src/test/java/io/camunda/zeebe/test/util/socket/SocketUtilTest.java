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
    // then
    // tests bind fixed defaults below 10000 (e.g. 8080, 9200) and the Zeebe defaults 26500-26502
    // later, which the allocator cannot detect when it hands out a port
    assertThat(ports).allSatisfy(port -> assertThat(port).isBetween(10000, 25999));
  }
}

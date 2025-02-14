/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl.rocksdb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

public class RocksDBMetricTest {
  @Test
  public void shouldBeNamedCorrectly() {
    final var metrics = ZeebeRocksDBMetricExporter.allMeterDocumentations();
    assertThat(metrics)
        .allSatisfy(
            m -> {
              assertThat(m.getName()).startsWith("zeebe.rocksdb.");
              assertThat(m.getName()).doesNotContain("..");
              assertThat(m.getName()).doesNotContain("-");
              assertThat(m.getName()).doesNotContain("_");
            });
    final var metric =
        Arrays.stream(metrics)
            .filter(m -> m.getName().equals("zeebe.rocksdb.memory.cur.size.all.mem.tables"))
            .findFirst();
    assertThat(metric).isPresent();
  }
}

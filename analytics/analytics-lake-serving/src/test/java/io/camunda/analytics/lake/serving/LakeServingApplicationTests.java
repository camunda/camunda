/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Proves the Spring context loads against a bare (still-empty) warehouse directory -- no lake/
 * subdirectory, no tables yet. {@link io.camunda.analytics.lake.serving.duckdb.LakeViewRegistry}
 * must tolerate this gracefully (log and skip, never fail startup); required-property validation
 * itself is covered by not needing a separate test here since a missing property would already fail
 * this test's own context startup.
 */
@SpringBootTest
class LakeServingApplicationTests {

  @TempDir private static Path warehouseDir;

  @DynamicPropertySource
  static void lakeServingProperties(final DynamicPropertyRegistry registry) {
    registry.add("lake.serving.warehouse-dir", () -> warehouseDir.toString());
  }

  @Test
  void shouldLoadContextAgainstAnEmptyWarehouse() {
    // Context loading itself is the assertion (a failing @PostConstruct discovery pass, or a
    // rejected LakeServingProperties binding, would fail this test before it ever gets here).
    assertThat(warehouseDir).exists();
  }
}

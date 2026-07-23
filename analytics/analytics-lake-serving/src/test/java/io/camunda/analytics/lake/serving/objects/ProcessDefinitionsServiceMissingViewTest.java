/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.objects;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.analytics.lake.serving.support.ParquetFixtures;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A warehouse that predates the {@code process_definitions} table (only an unrelated table exists):
 * {@link ProcessDefinitionsService} must degrade to a clean not-found rather than error, same
 * open/closed rule as every other object-fabric read here.
 */
@SpringBootTest
class ProcessDefinitionsServiceMissingViewTest {

  @TempDir private static Path warehouseDir;

  @Autowired private ProcessDefinitionsService processDefinitionsService;

  @DynamicPropertySource
  static void lakeServingProperties(final DynamicPropertyRegistry registry) {
    // Some other table exists (an older warehouse), but never process_definitions.
    ParquetFixtures.writeTable(warehouseDir, "objects", 1);
    registry.add("lake.serving.warehouse-dir", () -> warehouseDir.toString());
  }

  @Test
  void shouldReportNotFoundWhenTheViewDoesNotExistYet() {
    assertThatThrownBy(() -> processDefinitionsService.find("orderProcess", 1))
        .isInstanceOf(NoSuchElementException.class);
  }
}

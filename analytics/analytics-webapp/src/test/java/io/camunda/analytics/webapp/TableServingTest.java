/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.webapp.ServingTestSupport.Fixture;
import java.util.List;
import java.util.Map;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Read-path tests for raw tables: rows written to the serving store, read back through the table
 * executor.
 */
final class TableServingTest {

  private static final String TABLE = "raw-completed-instances";

  private Fixture fixture;
  private TableRepository repository;

  @BeforeEach
  void setUp() {
    fixture = ServingTestSupport.create();
    repository = fixture.tableRepository();
  }

  @AfterEach
  void tearDown() {
    fixture.datasetStore().close();
    fixture.metadataStore().close();
  }

  @Test
  void shouldListTheStandardTables() {
    assertThat(repository.listTables()).contains(TABLE);
  }

  @Test
  void shouldReadRawTableRowsBackFromTheServingStore() {
    // given two completed-instance rows written straight to the table (as Stage 1 would)
    ServingTestSupport.seedRow(
        fixture, TABLE, "1001", List.of("order-process", 1_500L, false, "EU"));
    ServingTestSupport.seedRow(
        fixture, TABLE, "1002", List.of("order-process", 42_000L, true, "US"));

    // when the table is read
    final List<Map<String, Object>> rows = repository.rows(TABLE, 100);

    // then both rows come back with their declared, typed column values
    assertThat(rows)
        .extracting(
            r -> r.get("bpmnProcessId"),
            r -> r.get("durationMs"),
            r -> r.get("hadIncident"),
            r -> r.get("var.region"))
        .containsExactlyInAnyOrder(
            Tuple.tuple("order-process", 1_500L, false, "EU"),
            Tuple.tuple("order-process", 42_000L, true, "US"));
  }

  @Test
  void shouldReturnAnEmptyListWhenNothingWritten() {
    assertThat(repository.rows(TABLE, 100)).isEmpty();
  }
}

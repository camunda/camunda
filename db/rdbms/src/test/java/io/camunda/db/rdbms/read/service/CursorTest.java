/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.sql.columns.ProcessInstanceSearchColumn;
import io.camunda.db.rdbms.sql.columns.SearchColumn;
import io.camunda.db.rdbms.sql.columns.SearchColumnUtils;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CursorTest {
  Cursor cursor = new Cursor();

  public static Stream<Arguments> provideSearchColumns() {
    return SearchColumnUtils.findAll().stream()
        .map(column -> Arguments.of(column.getEntityClass().getSimpleName(), column));
  }

  @ParameterizedTest(name = "#{0}#{1}")
  @MethodSource("provideSearchColumns")
  void encodeDecodeSearchColumn(final String className, final SearchColumn column) {
    final var columns = List.of(column);
    final var entity = Instancio.create(column.getEntityClass());

    final var encodedCursor = cursor.encode(entity, columns);
    final var decodedValues = cursor.decode(encodedCursor, columns);

    final var encodedValue = columns.stream().map(c -> c.getPropertyValue(entity)).toArray();
    assertThat(encodedValue).isEqualTo(decodedValues);
  }

  @Test
  void encodeDecodeSearchColumns() {
    final var columns =
        List.of(
            ProcessInstanceSearchColumn.PROCESS_DEFINITION_NAME,
            ProcessInstanceSearchColumn.START_DATE);
    final var entity = Instancio.create(ProcessInstanceEntity.class);

    final var encodedCursor = cursor.encode(entity, columns);
    final var decodedValues = cursor.decode(encodedCursor, columns);

    final var encodedValue = columns.stream().map(c -> c.getPropertyValue(entity)).toArray();
    assertThat(encodedValue).isEqualTo(decodedValues);
  }

  @Test
  void encodeDecodeEmptySearchColumns() {
    final List<SearchColumn> columns = Collections.emptyList();
    final var entity = Instancio.create(ProcessInstanceEntity.class);

    final var encodedCursor = cursor.encode(entity, columns);
    final var decodedValues = cursor.decode(encodedCursor, columns);

    final var encodedValue = columns.stream().map(c -> c.getPropertyValue(entity)).toArray();
    assertThat(encodedValue).isEqualTo(decodedValues);
  }
}

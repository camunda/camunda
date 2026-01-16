/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.db.rdbms.sql.columns.ProcessInstanceSearchColumn;
import io.camunda.db.rdbms.sql.columns.SearchColumn;
import io.camunda.db.rdbms.sql.columns.SearchColumnUtils;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.CamundaSearchException.Reason;
import java.util.List;
import java.util.stream.Stream;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CursorTest {

  public static Stream<Arguments> provideSearchColumns() {
    return SearchColumnUtils.findAll().stream()
        .map(column -> Arguments.of(column.getEntityClass().getSimpleName(), column));
  }

  @ParameterizedTest(name = "#{0}#{1}")
  @MethodSource("provideSearchColumns")
  void shouldEncodeDecodeWithSearchColumn(
      final String className, final SearchColumn<Object> column) {
    final var columns = List.of(column);
    final var entity = Instancio.create(column.getEntityClass());

    final var encodedCursor = Cursor.encode(entity, columns);
    final var decodedValues = Cursor.decode(encodedCursor, columns);

    final var encodedValue = columns.stream().map(c -> c.getPropertyValue(entity)).toArray();
    assertThat(encodedValue).isEqualTo(decodedValues);
  }

  @Test
  void shouldEncodeDecodeWithMultipleSearchColumns() {
    final List<SearchColumn<ProcessInstanceEntity>> columns =
        List.of(
            ProcessInstanceSearchColumn.PROCESS_DEFINITION_NAME,
            ProcessInstanceSearchColumn.START_DATE);
    final var entity = Instancio.create(ProcessInstanceEntity.class);

    final var encodedCursor = Cursor.encode(entity, columns);
    final var decodedValues = Cursor.decode(encodedCursor, columns);

    final var encodedValue = columns.stream().map(c -> c.getPropertyValue(entity)).toArray();
    assertThat(encodedValue).isEqualTo(decodedValues);
  }

  @Test
  void shouldEncodeToNullWithEmptySearchColumns() {
    final var entity = Instancio.create(ProcessInstanceEntity.class);
    assertThat(Cursor.encode(entity, List.of())).isNull();
  }

  @Test
  void shouldEncodeToNullWithNullSearchColumns() {
    final var entity = Instancio.create(ProcessInstanceEntity.class);
    assertThat(Cursor.encode(entity, null)).isNull();
  }

  @Test
  void shouldEncodeToNullWithNullEntity() {
    assertThat(Cursor.encode(null, List.of(ProcessInstanceSearchColumn.PROCESS_DEFINITION_NAME)))
        .isNull();
  }

  @Test
  void shouldDecodeToNullWithEmptySearchColumns() {
    assertThat(Cursor.decode("ignored", List.of())).isNull();
  }

  @Test
  void shouldDecodeToNullWithNullSearchColumns() {
    assertThat(Cursor.decode("ignored", null)).isNull();
  }

  @Test
  void shouldDecodeToNullWithNullCursor() {
    assertThat(Cursor.decode(null, List.of(ProcessInstanceSearchColumn.PROCESS_DEFINITION_NAME)))
        .isNull();
  }

  @Test
  void shouldDecodeToNullWithEmptyCursor() {
    assertThat(Cursor.decode("", List.of(ProcessInstanceSearchColumn.PROCESS_DEFINITION_NAME)))
        .isNull();
  }

  @Test
  void shouldFailDecodeWithInvalidCursor() {
    assertThatThrownBy(
            () ->
                Cursor.decode(
                    "invalid_cursor", List.of(ProcessInstanceSearchColumn.PROCESS_DEFINITION_NAME)))
        .isInstanceOf(CamundaSearchException.class)
        .hasMessageContaining("Cannot decode pagination cursor 'invalid_cursor'")
        .extracting("reason")
        .isEqualTo(Reason.INVALID_ARGUMENT);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.multidb;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RdbmsSchemaInitializerTest {

  @Test
  void shouldStripSingleLeadingCommentLine() {
    // given
    final String input = "-- create_process_definition_table\nCREATE TABLE FOO (ID NUMBER)";

    // when
    final String result = RdbmsSchemaInitializer.stripLeadingComments(input);

    // then
    assertThat(result).isEqualTo("CREATE TABLE FOO (ID NUMBER)");
  }

  @Test
  void shouldStripMultipleLeadingCommentLines() {
    // given
    final String input = "-- comment 1\n-- comment 2\nCREATE TABLE FOO (ID NUMBER)";

    // when
    final String result = RdbmsSchemaInitializer.stripLeadingComments(input);

    // then
    assertThat(result).isEqualTo("CREATE TABLE FOO (ID NUMBER)");
  }

  @Test
  void shouldNotStripCommentsThatFollowSql() {
    // given - a comment embedded inside the SQL body must not be removed
    final String input = "CREATE TABLE FOO (\n  -- column comment\n  ID NUMBER\n)";

    // when
    final String result = RdbmsSchemaInitializer.stripLeadingComments(input);

    // then - the embedded comment is preserved because it does not appear at the leading position
    assertThat(result).isEqualTo("CREATE TABLE FOO (\n  -- column comment\n  ID NUMBER\n)");
  }

  @Test
  void shouldReturnEmptyStringForCommentOnlyInput() {
    // given
    final String input = "-- only a comment";

    // when
    final String result = RdbmsSchemaInitializer.stripLeadingComments(input);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnInputUnchangedWhenNoLeadingComment() {
    // given
    final String input = "CREATE INDEX IDX_FOO ON BAR(BAZ)";

    // when
    final String result = RdbmsSchemaInitializer.stripLeadingComments(input);

    // then
    assertThat(result).isEqualTo(input);
  }
}

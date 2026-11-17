/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import liquibase.exception.ChangeLogParseException;
import liquibase.exception.CommandExecutionException;
import liquibase.exception.DatabaseException;
import liquibase.exception.LiquibaseException;
import liquibase.exception.LockException;
import liquibase.exception.MigrationFailedException;
import liquibase.exception.ValidationFailedException;
import org.junit.jupiter.api.Test;

/** Which Liquibase failures the schema-initialization retry loop is told to stop on. */
class LiquibaseSchemaManagerClassificationTest {

  @Test
  void shouldClassifyAnEditedChangesetAsDeterministic() {
    // given - Liquibase reports a checksum mismatch nested two levels down
    final var nested =
        new LiquibaseException(
            new CommandExecutionException(mock(ValidationFailedException.class)));

    // when / then - the top-level type says nothing, so the cause chain has to be walked
    assertThat(LiquibaseSchemaManager.isDeterministicFailure(nested)).isTrue();
  }

  @Test
  void shouldClassifyAnUnparseableChangelogAsDeterministic() {
    // given / when / then - a changelog in this JAR cannot become well-formed while the node runs
    assertThat(
            LiquibaseSchemaManager.isDeterministicFailure(
                new ChangeLogParseException("malformed changelog")))
        .isTrue();
  }

  @Test
  void shouldNotClassifyAFailedChangesetAsDeterministic() {
    // given - a changeset that failed to apply, which is what a missing DDL grant surfaces as
    final var grantMissing = new MigrationFailedException();

    // when / then - ValidationFailedException extends this type; only the subtype is deterministic
    assertThat(LiquibaseSchemaManager.isDeterministicFailure(grantMissing)).isFalse();
  }

  @Test
  void shouldNotClassifyStorageFailuresAsDeterministic() {
    // given / when / then - a peer holding the changelog lock, or an unreachable database, are both
    // repairable without restarting the node
    assertThat(LiquibaseSchemaManager.isDeterministicFailure(new LockException("lock held")))
        .isFalse();
    assertThat(
            LiquibaseSchemaManager.isDeterministicFailure(
                new DatabaseException("connection refused")))
        .isFalse();
  }
}

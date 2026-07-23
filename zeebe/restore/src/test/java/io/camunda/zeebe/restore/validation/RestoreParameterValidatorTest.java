/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore.validation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreRequest;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

final class RestoreParameterValidatorTest {

  private static final String EARLIER = "2026-01-01T10:00:00Z";
  private static final String LATER = "2026-01-01T12:00:00Z";
  private static final List<Long> BACKUP_ID = List.of(1L);
  private static final List<Long> MULTIPLE_BACKUP_IDS = List.of(1L, 2L);
  private static final List<Long> NO_BACKUP_IDS = List.of();

  private static final String BOTH_MESSAGE =
      "Cannot specify both backupId and from/to parameters. Choose one approach.";
  private static final String CONTINUOUS_MESSAGE =
      "Time range restore (from/to) is only supported for continuous backups.";
  private static final String NO_BACKUP_ID_MESSAGE = "No backupId specified";
  private static final String MULTIPLE_BACKUP_IDS_MESSAGE =
      "Cannot restore from multiple backups against this database type";

  private final RestoreValidator validator = new RestoreValidator(1, null, partitionId -> 1L);

  private static RestoreRequest request(
      final List<Long> backupIds,
      final @Nullable String from,
      final @Nullable String to,
      final String databaseType,
      final boolean continuousBackups) {
    return new RestoreRequest(
        "default", backupIds, from, to, databaseType, continuousBackups, false);
  }

  @Test
  void shouldAcceptDatabaseTypeCaseInsensitively() {
    // when / then
    assertThatCode(
            () ->
                validator.validateParameters(
                    request(BACKUP_ID, null, null, "ElasticSearch", false)))
        .doesNotThrowAnyException();
  }

  @Nested
  final class Rdbms {

    private static final String DB = "rdbms";

    @Test
    void shouldAcceptBackupIdOnly() {
      // when / then
      assertThatCode(() -> validator.validateParameters(request(BACKUP_ID, null, null, DB, false)))
          .doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptTimeRangeWithContinuousBackups() {
      // when / then
      assertThatCode(
              () -> validator.validateParameters(request(NO_BACKUP_IDS, EARLIER, LATER, DB, true)))
          .doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptNoParameters() {
      // when / then
      assertThatCode(
              () -> validator.validateParameters(request(NO_BACKUP_IDS, null, null, DB, false)))
          .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectBackupIdAndTimeRangeTogether() {
      // when / then
      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(
              () -> validator.validateParameters(request(BACKUP_ID, EARLIER, null, DB, true)))
          .withMessage(BOTH_MESSAGE);
    }

    @Test
    void shouldRejectTimeRangeWithoutContinuousBackups() {
      // when / then
      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(
              () -> validator.validateParameters(request(NO_BACKUP_IDS, EARLIER, LATER, DB, false)))
          .withMessage(CONTINUOUS_MESSAGE);
    }

    @Test
    void shouldRejectSingleBoundWithoutContinuousBackups() {
      // when / then
      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(
              () -> validator.validateParameters(request(NO_BACKUP_IDS, EARLIER, null, DB, false)))
          .withMessage(CONTINUOUS_MESSAGE);
    }

    @Test
    void shouldRejectFromAfterTo() {
      // when / then
      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(
              () -> validator.validateParameters(request(NO_BACKUP_IDS, LATER, EARLIER, DB, true)))
          .withMessage(
              "Invalid time range: from (%s) must be before to (%s)"
                  .formatted(Instant.parse(LATER), Instant.parse(EARLIER)));
    }

    @Test
    void shouldRejectInvalidTimestamp() {
      // when / then
      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(
              () ->
                  validator.validateParameters(
                      request(NO_BACKUP_IDS, "not-a-timestamp", null, DB, true)))
          .withMessage("Invalid from timestamp 'not-a-timestamp': must be an ISO 8601 date-time.");
    }

    @Test
    void shouldRejectWhenNeitherBackupIdsNorExportedPositionSupplierAreProvided() {
      // given - no backupIds and no exported-position supplier means there is no way to resolve a
      // restore point at all
      final var validatorWithoutExportedPositions = new RestoreValidator(1, null, null);

      // when / then
      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(
              () ->
                  validatorWithoutExportedPositions.validateParameters(
                      request(NO_BACKUP_IDS, null, null, DB, false)))
          .withMessage(
              "Cannot resolve a restore point: no backupId was specified and no "
                  + "exported-position data is available. Configure RDBMS as the secondary "
                  + "storage to enable time-range restores, or specify a backupId.");
    }
  }

  @Nested
  final class Elasticsearch {

    private static final String DB = "elasticsearch";

    @Test
    void shouldAcceptBackupIdOnly() {
      // when / then
      assertThatCode(() -> validator.validateParameters(request(BACKUP_ID, null, null, DB, false)))
          .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectNoParameters() {
      // when / then
      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(
              () -> validator.validateParameters(request(NO_BACKUP_IDS, null, null, DB, false)))
          .withMessage(NO_BACKUP_ID_MESSAGE);
    }

    @Test
    void shouldRejectTimeRange() {
      // when / then
      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(
              () -> validator.validateParameters(request(NO_BACKUP_IDS, EARLIER, LATER, DB, true)))
          .withMessage("Time range restore (from/to) is not supported for elasticsearch.");
    }

    @Test
    void shouldRejectBackupIdAndTimeRangeTogether() {
      // when / then
      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(
              () -> validator.validateParameters(request(BACKUP_ID, EARLIER, null, DB, true)))
          .withMessage(BOTH_MESSAGE);
    }

    @Test
    void shouldRejectMultipleBackupIds() {
      // when / then
      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(
              () ->
                  validator.validateParameters(request(MULTIPLE_BACKUP_IDS, null, null, DB, false)))
          .withMessage(MULTIPLE_BACKUP_IDS_MESSAGE);
    }
  }

  @Nested
  final class InvalidDatabaseType {

    @Test
    void shouldRejectUnknownDatabaseType() {
      // when / then
      assertThatExceptionOfType(IllegalStateException.class)
          .isThrownBy(
              () -> validator.validateParameters(request(BACKUP_ID, null, null, "mongodb", false)))
          .withMessage("Invalid database type: mongodb");
    }
  }
}

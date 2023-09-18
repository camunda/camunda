/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration.to_8_3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.deployment.VersionInfo;
import io.camunda.zeebe.engine.state.immutable.MigrationState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

public class ProcessDefinitionVersionMigrationTest {

  final ProcessDefinitionVersionMigration sut = new ProcessDefinitionVersionMigration();

  private static final class LegacyProcessVersionState {
    private final DbString processIdKey;
    private final ColumnFamily<DbString, VersionInfo> processVersionInfoColumnFamily;

    public LegacyProcessVersionState(
        final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
      processIdKey = new DbString();
      processVersionInfoColumnFamily =
          zeebeDb.createColumnFamily(
              ZbColumnFamilies.DEPRECATED_PROCESS_VERSION,
              transactionContext,
              processIdKey,
              new VersionInfo());
    }

    public void insertProcessVersion(final String processId, final int version) {
      processIdKey.wrapString(processId);
      final var value = new VersionInfo().setHighestVersionIfHigher(version);
      processVersionInfoColumnFamily.insert(processIdKey, value);
    }
  }

  @Nested
  class MockBasedTests {

    @Test
    void migrationNeededWhenMigrationNotFinished() {
      // given
      final var mockProcessingState = mock(ProcessingState.class);
      final var migrationState = mock(MigrationState.class);
      when(mockProcessingState.getMigrationState()).thenReturn(migrationState);
      when(migrationState.isMigrationFinished(anyString())).thenReturn(false);

      // when
      final var actual = sut.needsToRun(mockProcessingState);

      // then
      assertThat(actual).isTrue();
    }
  }

  @Nested
  @ExtendWith(ProcessingStateExtension.class)
  class ProcessVersionMigrationTest {

    private ZeebeDb<ZbColumnFamilies> zeebeDb;
    private MutableProcessingState processingState;
    private TransactionContext transactionContext;
    private LegacyProcessVersionState legacyState;
    private DbString processIdKey;
    private ColumnFamily<DbString, VersionInfo> nextValueColumnFamily;

    @BeforeEach
    void setup() {
      legacyState = new LegacyProcessVersionState(zeebeDb, transactionContext);
      processIdKey = new DbString();
      nextValueColumnFamily =
          zeebeDb.createColumnFamily(
              ZbColumnFamilies.DEPRECATED_PROCESS_VERSION,
              transactionContext,
              processIdKey,
              new VersionInfo());
    }

    @Test
    void shouldMigrateProcessVersion() {
      // given
      final String processId = "processId";
      legacyState.insertProcessVersion(processId, 5);

      // when
      sut.runMigration(processingState);

      // then
      processIdKey.wrapString(processId);
      final var versionInfo = nextValueColumnFamily.get(processIdKey);
      assertThat(versionInfo.getHighestVersion()).isEqualTo(5);
      assertThat(versionInfo.getKnownVersions()).containsExactly(1L, 2L, 3L, 4L, 5L);
    }

    @Test
    void shouldNotSetKnownVersionsIfHighestVersionIsZero() {
      // given
      final String processId = "processId";
      legacyState.insertProcessVersion(processId, 0);

      // when
      sut.runMigration(processingState);

      // then
      processIdKey.wrapString(processId);
      final var versionInfo = nextValueColumnFamily.get(processIdKey);
      assertThat(versionInfo.getHighestVersion()).isEqualTo(0);
      assertThat(versionInfo.getKnownVersions()).isEmpty();
    }
  }
}

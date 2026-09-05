/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.db.rdbms.RdbmsSchemaVersionStore;
import io.camunda.db.rdbms.exception.RdbmsSchemaVersionIncompatibleException;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.zeebe.util.VersionUtil;
import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Verifies {@link RdbmsSchemaVersionStore#tableExists} finds {@code RDBMS_SCHEMA_VERSION} on every
 * real database vendor, not just H2 -- unquoted identifiers fold differently per vendor (e.g.
 * PostgreSQL/MySQL/MariaDB lower case vs. H2/Oracle/MSSQL upper case), which the check previously
 * missed.
 */
@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
final class RdbmsSchemaVersionStoreIT {

  @TestTemplate
  void shouldFindExistingSchemaVersionRegardlessOfVendorIdentifierCasing(
      final CamundaRdbmsTestApplication testApplication) {
    final DataSource dataSource = testApplication.bean(DataSource.class);
    final var versionStore = new RdbmsSchemaVersionStore(dataSource, "", VersionUtil.getVersion());

    // The test application's own startup migration already recorded the running version, so a
    // second, independently constructed store checking against that same version is the
    // happy-path case: the version matches, so this must pass without error.
    assertThatCode(versionStore::checkCompatibility).doesNotThrowAnyException();
  }

  @TestTemplate
  void shouldDetectIncompatibleUpgradePathRegardlessOfVendorIdentifierCasing(
      final CamundaRdbmsTestApplication testApplication) throws Exception {
    final DataSource dataSource = testApplication.bean(DataSource.class);
    final var versionStore = new RdbmsSchemaVersionStore(dataSource, "", VersionUtil.getVersion());

    // Rewind the version the test application's own startup migration already recorded, far
    // enough behind the running version to be an illegal, minor-version-skipping upgrade path.
    // Plain DML resolves the unquoted table name via the vendor's own identifier folding, the
    // same way the production upsert in RdbmsSchemaVersionStore#recordCurrentVersion does, so this
    // does not itself depend on the tableExists behavior under test.
    try (final var connection = dataSource.getConnection()) {
      final var autoCommit = connection.getAutoCommit();
      connection.setAutoCommit(false);
      try (final var statement = connection.createStatement()) {
        statement.executeUpdate("UPDATE RDBMS_SCHEMA_VERSION SET VERSION = '8.0.0' WHERE ID = 1");
      }
      connection.commit();
      connection.setAutoCommit(autoCommit);
    }

    try {
      assertThatThrownBy(versionStore::checkCompatibility)
          .isInstanceOf(RdbmsSchemaVersionIncompatibleException.class);
    } finally {
      // Restore the version a real boot would have recorded, so any later test reusing this
      // vendor's shared, cached test application observes a consistent, correctly-migrated state.
      versionStore.recordCurrentVersion();
    }
  }
}

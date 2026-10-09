/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import static io.camunda.db.rdbms.RdbmsTableNames.SCHEMA_VERSION;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.PurgeMapper;
import org.junit.jupiter.api.Test;

class RdbmsPurgerTest {

  @Test
  void shouldPreserveSchemaVersion() {
    // given
    final var purgeMapper = mock(PurgeMapper.class);
    final var purger = new RdbmsPurger(purgeMapper, mock(VendorDatabaseProperties.class));

    // when
    purger.purgeRdbms();

    // then
    verify(purgeMapper).truncateTable("PROCESS_INSTANCE");
    verify(purgeMapper, never()).truncateTable(SCHEMA_VERSION);
  }
}

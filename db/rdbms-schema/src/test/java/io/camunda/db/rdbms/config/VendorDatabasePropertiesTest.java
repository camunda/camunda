/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Properties;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class VendorDatabasePropertiesTest {

  @ParameterizedTest(name = "{0} folds \"Tenant_Process_Instance\" to \"{1}\"")
  @CsvSource({
    "postgresql, tenant_process_instance",
    "oracle, TENANT_PROCESS_INSTANCE",
    "mysql, TENANT_PROCESS_INSTANCE",
    "mariadb, TENANT_PROCESS_INSTANCE",
    "mssql, Tenant_Process_Instance",
    "h2, Tenant_Process_Instance",
  })
  void shouldFoldTableIdentifierPerVendor(final String databaseId, final String expectedFold)
      throws IOException {
    // given
    final var properties = VendorDatabasePropertiesLoader.load(databaseId);

    // when
    final var folded = properties.foldTableIdentifier("Tenant_Process_Instance");

    // then
    assertThat(folded).isEqualTo(expectedFold);
  }

  @ParameterizedTest(name = "strategy \"{0}\" means usesCatalogRowCountStatistics = {1}")
  @CsvSource({
    "catalog, true",
    "live, false",
  })
  void shouldInterpretTableRowCountStrategy(
      final String strategy, final boolean expectedUsesCatalogStatistics) {
    // given
    final var properties = minimalProperties(strategy, "none");

    // when / then
    assertThat(properties.usesCatalogRowCountStatistics()).isEqualTo(expectedUsesCatalogStatistics);
  }

  private static VendorDatabaseProperties minimalProperties(
      final String tableRowCountStrategy, final String tableRowCountIdentifierCase) {
    final var props = new Properties();
    props.put(VendorDatabaseProperties.DATABASE_ID, "test-vendor");
    props.put("variableValue.previewSize", "8191");
    props.put("userCharColumn.size", "256");
    props.put("errorMessage.size", "4000");
    props.put("treePath.size", "8191");
    props.put("disableFkBeforeTruncate", "false");
    props.put("tableRowCount.strategy", tableRowCountStrategy);
    props.put("tableRowCount.identifierCase", tableRowCountIdentifierCase);
    return new VendorDatabaseProperties(props);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db;

import static io.camunda.configuration.api.physicaltenants.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import io.camunda.application.commons.rdbms.RdbmsDataSources;
import io.camunda.db.rdbms.write.RdbmsMapperBundle;
import io.camunda.db.rdbms.write.service.RdbmsPurger;
import io.camunda.it.rdbms.db.util.RdbmsDataJdbcTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@RdbmsDataJdbcTest
@TestPropertySource(
    properties = {"spring.liquibase.enabled=false", "camunda.data.secondary-storage.type=rdbms"})
public class PurgerCompletenessIT {

  @Autowired JdbcTemplate jdbcTemplate;

  @Autowired private Map<String, RdbmsMapperBundle> rdbmsMapperBundleMap;
  @Autowired private RdbmsDataSources rdbmsDataSources;

  @Test
  public void shouldFindWithSpecificFilter() {
    final var spy = spy(rdbmsMapperBundleMap.get(DEFAULT_PHYSICAL_TENANT_ID).purgeMapper());

    final var rdbmsPurger =
        new RdbmsPurger(spy, rdbmsDataSources.vendorPropertiesFor(DEFAULT_PHYSICAL_TENANT_ID));

    rdbmsPurger.purgeRdbms();

    final var tablesToBeTruncated = getAllCamundaTableNames();

    final ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
    verify(spy, Mockito.atLeastOnce()).truncateTable(argumentCaptor.capture());

    final var actuallyTruncated = argumentCaptor.getAllValues();

    assertThat(actuallyTruncated)
        .describedAs("should truncate every Camunda table")
        .containsExactlyInAnyOrderElementsOf(tablesToBeTruncated);
    assertThat(tablesToBeTruncated)
        .describedAs("should only truncate Camunda tables")
        .containsExactlyInAnyOrderElementsOf(actuallyTruncated);
  }

  private List<String> getAllCamundaTableNames() {
    return jdbcTemplate
        .queryForList(
            "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME NOT IN ('DATABASECHANGELOG', 'DATABASECHANGELOGLOCK')")
        .stream()
        .map(row -> row.get("TABLE_NAME").toString())
        .toList();
  }
}

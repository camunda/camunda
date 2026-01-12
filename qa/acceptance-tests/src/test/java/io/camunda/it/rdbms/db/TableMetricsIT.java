/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db;

import io.camunda.db.rdbms.sql.TableMetricsMapper;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class TableMetricsIT {

  @TestTemplate
  public void shouldCountTableRows(final CamundaRdbmsTestApplication testApplication) {
    final var tableMetricsMapper = testApplication.bean(TableMetricsMapper.class);

    final String tableName = "AUTHORIZATIONS";

    // Get the count using the optimized vendor-specific query
    final long rowCount = tableMetricsMapper.countTableRows(tableName);

    // having any number here is sufficient for the test because it shows that the SQL is working
    // asserting for a specific number would make the test fragile as the statistics are
    // eventual consistent and only a rough number for each vendor
  }
}

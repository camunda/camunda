/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.query.ProcessDefinitionInstanceStatisticsQuery;
import io.camunda.security.reader.ResourceAccessChecks;
import org.junit.jupiter.api.Test;

class ProcessDefinitionInstanceStatisticsDbReaderTest {
  private final ProcessDefinitionInstanceStatisticsDbReader reader =
      new ProcessDefinitionInstanceStatisticsDbReader();

  @Test
  void shouldReturnEmptyResultWhenNotImplemented() {
    final ProcessDefinitionInstanceStatisticsQuery query =
        new ProcessDefinitionInstanceStatisticsQuery(null, null, null);
    final ResourceAccessChecks resourceAccessChecks = ResourceAccessChecks.disabled();

    final var result = reader.aggregate(query, resourceAccessChecks);

    assertThat(result.total()).isZero();
    assertThat(result.items()).isEmpty();
  }
}

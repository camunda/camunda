/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.sql.ProcessInstanceMapper;
import io.camunda.search.entities.ProcessFlowNodeStatisticsEntity;
import io.camunda.search.query.ProcessInstanceFlowNodeStatisticsQuery;
import io.camunda.security.reader.ResourceAccessChecks;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProcessInstanceStatisticsDbReaderTest {
  private final ProcessInstanceMapper processInstanceMapper = mock(ProcessInstanceMapper.class);
  private final ProcessInstanceStatisticsDbReader reader =
      new ProcessInstanceStatisticsDbReader(
          processInstanceMapper, AbstractEntityReaderTest.TEST_CONFIG);

  @Test
  void shouldReturnStatistics() {
    final var expected =
        List.of(
            new ProcessFlowNodeStatisticsEntity("node1", 10L, 5L, 2L, 3L),
            new ProcessFlowNodeStatisticsEntity("node2", 8L, 3L, 1L, 4L));
    when(processInstanceMapper.flowNodeStatistics(123L)).thenReturn(expected);

    final ProcessInstanceFlowNodeStatisticsQuery query =
        new ProcessInstanceFlowNodeStatisticsQuery(
            new io.camunda.search.filter.ProcessInstanceStatisticsFilter(123L));
    final ResourceAccessChecks resourceAccessChecks = ResourceAccessChecks.disabled();

    final var result = reader.aggregate(query, resourceAccessChecks);

    assertThat(result).isEqualTo(expected);
    verify(processInstanceMapper).flowNodeStatistics(123L);
  }
}

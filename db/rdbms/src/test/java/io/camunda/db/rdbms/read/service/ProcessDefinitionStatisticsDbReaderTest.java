/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.sql.ProcessDefinitionMapper;
import io.camunda.search.entities.ProcessFlowNodeStatisticsEntity;
import io.camunda.search.query.ProcessDefinitionFlowNodeStatisticsQuery;
import io.camunda.security.reader.ResourceAccessChecks;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProcessDefinitionStatisticsDbReaderTest {
  private final ProcessDefinitionMapper processDefinitionMapper =
      mock(ProcessDefinitionMapper.class);
  private final ProcessDefinitionStatisticsDbReader reader =
      new ProcessDefinitionStatisticsDbReader(
          processDefinitionMapper, AbstractEntityReaderTest.TEST_CONFIG);

  @Test
  void shouldReturnStatistics() {
    final var expected =
        List.of(
            new ProcessFlowNodeStatisticsEntity("node1", 10L, 5L, 2L, 3L),
            new ProcessFlowNodeStatisticsEntity("node2", 8L, 3L, 1L, 4L));
    when(processDefinitionMapper.flowNodeStatistics(any())).thenReturn(expected);

    final ProcessDefinitionFlowNodeStatisticsQuery query =
        new ProcessDefinitionFlowNodeStatisticsQuery(null);
    final ResourceAccessChecks resourceAccessChecks = ResourceAccessChecks.disabled();

    final var result = reader.aggregate(query, resourceAccessChecks);

    assertThat(result).isEqualTo(expected);
    verify(processDefinitionMapper).flowNodeStatistics(any());
  }
}

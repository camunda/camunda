/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedInstancesOnlyFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ExecutedFlowNodeFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import io.camunda.optimize.service.db.reader.AgentElementReader;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExecutedAgentElementFilterTest {

  private final AgentElementReader reader = mock(AgentElementReader.class);

  @Test
  void shouldKeepOnlyProcessInstancesThatRanAnAgentElementOfTheSelectedProcesses() {
    // given
    when(reader.getAgentFlowNodeIds(List.of("loan")))
        .thenReturn(List.of("risk-agent", "doc-agent"));
    final ProcessReportDataDto report = report(ProcessViewEntity.PROCESS_INSTANCE, "loan", "loan");

    // when
    ExecutedAgentElementFilter.addTo(report, reader);

    // then
    assertThat(report.getFilter()).hasSize(2);
    assertThat(report.getFilter().get(0)).isInstanceOf(CompletedInstancesOnlyFilterDto.class);
    final ExecutedFlowNodeFilterDto executed =
        (ExecutedFlowNodeFilterDto) report.getFilter().get(1);
    assertThat(executed.getData().getOperator()).isEqualTo(MembershipFilterOperator.IN);
    assertThat(executed.getData().getValues()).containsExactly("risk-agent", "doc-agent");
  }

  @Test
  void shouldMatchNoInstanceWhenTheProcessHasNoAgentElement() {
    // given
    when(reader.getAgentFlowNodeIds(any())).thenReturn(List.of());
    final ProcessReportDataDto report = report(ProcessViewEntity.FLOW_NODE, "invoice");

    // when
    ExecutedAgentElementFilter.addTo(report, reader);

    // then
    assertThat(((ExecutedFlowNodeFilterDto) report.getFilter().get(1)).getData().getValues())
        .containsExactly(ExecutedAgentElementFilter.NO_AGENT_ELEMENT);
  }

  @Test
  void shouldLeaveAgentRunTilesAlone() {
    // given
    final ProcessReportDataDto report = report(ProcessViewEntity.AGENT_RUN, "loan");

    // when
    ExecutedAgentElementFilter.addTo(report, reader);

    // then
    assertThat(report.getFilter()).hasSize(1);
    verify(reader, never()).getAgentFlowNodeIds(any());
  }

  private static ProcessReportDataDto report(
      final ProcessViewEntity entity, final String... definitionKeys) {
    return ProcessReportDataDto.builder()
        .definitions(List.of(definitionKeys).stream().map(ReportDataDefinitionDto::new).toList())
        .view(new ProcessViewDto(entity, ViewProperty.FREQUENCY))
        .filter(ProcessFilterBuilder.filter().completedInstancesOnly().add().buildList())
        .agenticControlReport(true)
        .build();
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.report;

import static io.camunda.optimize.AgenticInstanceFixtures.agenticInstanceWithTokens;
import static io.camunda.optimize.AgenticInstanceFixtures.repeat;
import static io.camunda.optimize.AgenticInstanceFixtures.resolvedIncident;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import io.camunda.optimize.dto.optimize.query.report.SingleReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import io.camunda.optimize.service.db.writer.ReportWriter;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The agent insights incident rate divides by completed agentic process instances only. Without the
 * agentic baseline, Optimize divides the numerator by every completed instance in the range.
 */
class AgentInsightsPercentageBaselineIT extends AbstractBrokerlessZeebeCCSMIT {

  private static final String USER_ID = "testUser";

  @BeforeEach
  void enablePhase2() {
    setAgenticPhase2Enabled(true);
  }

  @AfterEach
  void disablePhase2() {
    setAgenticPhase2Enabled(false);
  }

  @Test
  void shouldDivideByCompletedAgenticInstancesOnly() {
    // given one agentic instance with an incident, one without, and eight non-agentic instances
    final String processKey = "agent-insights-process-" + UUID.randomUUID();
    final ProcessInstanceDto withIncident =
        resolvedIncident(agenticInstanceWithTokens(processKey, 100L, 50L));
    final ProcessInstanceDto withoutIncident =
        agenticInstanceWithTokens(processKey, 100L, 50L).build();
    final List<ProcessInstanceDto> nonAgentic =
        repeat(8, () -> completedInstance(processKey).build());
    persistProcessInstances(
        Stream.concat(Stream.of(withIncident, withoutIncident), nonAgentic.stream()).toList());
    final String reportId = createIncidentRateReport();

    // when
    final Object rate = evaluate(reportId).getFirstCommandResult().getFirstMeasureData();

    // then the denominator is the two agentic instances, not all ten
    assertThat(rate).isEqualTo(50.0);
  }

  private String createIncidentRateReport() {
    final ProcessReportDataDto data =
        ProcessReportDataDto.builder()
            .definitions(new ArrayList<>())
            .view(new ProcessViewDto(ProcessViewEntity.PROCESS_INSTANCE, ViewProperty.PERCENTAGE))
            .groupBy(new NoneGroupByDto())
            .filter(
                ProcessFilterBuilder.filter()
                    .completedInstancesOnly()
                    .add()
                    .hasAgentInstances()
                    .add()
                    .withResolvedIncident()
                    .add()
                    .buildList())
            .agentInsightsReport(true)
            .build();
    final String reportId = "agent-insights-incident-rate-" + UUID.randomUUID();
    embeddedOptimizeExtension
        .getBean(ReportWriter.class)
        .createOrUpdateSingleProcessReport(reportId, null, data, "Incident rate", null, null);
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();
    return reportId;
  }

  private SingleReportEvaluationResult<?> evaluate(final String reportId) {
    return (SingleReportEvaluationResult<?>)
        embeddedOptimizeExtension
            .getBean(ReportEvaluationService.class)
            .evaluateSavedReportWithAdditionalFilters(
                USER_ID,
                ZoneId.of("UTC"),
                reportId,
                new AdditionalProcessReportEvaluationFilterDto(List.of()),
                null)
            .getEvaluationResult();
  }

  private void setAgenticPhase2Enabled(final boolean enabled) {
    embeddedOptimizeExtension
        .getConfigurationService()
        .getAgenticPhase2Configuration()
        .setEnabled(enabled);
    embeddedOptimizeExtension.reloadConfiguration();
  }
}

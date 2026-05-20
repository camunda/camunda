/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.ReportType;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.BooleanVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.HasAgentInstancesFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.HasAgentInstancesFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.util.mapper.ObjectMapperFactory;
import io.camunda.optimize.service.util.mapper.OptimizeDateTimeFormatterFactory;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ObjectMapperFactoryTest {

  private ObjectMapperFactory objectMapperFactory;

  @BeforeEach
  public void init() {
    objectMapperFactory =
        new ObjectMapperFactory(new OptimizeDateTimeFormatterFactory().getObject());
  }

  /**
   * By default jackson fails if the external type id property is present but the actual property
   * not. In this case "reportType" is the external type id and "data" is the property.
   *
   * @see
   *     com.fasterxml.jackson.databind.DeserializationFeature#FAIL_ON_MISSING_EXTERNAL_TYPE_ID_PROPERTY
   */
  @Test
  public void testNoFailOnMissingReportDataAlthoughReportTypeSet() throws Exception {
    final ReportDefinitionDto reportDefinitionDto =
        createOptimizeMapper()
            .readValue(
                ObjectMapperFactoryTest.class.getResourceAsStream(
                    "/test/data/single-process-report-definition-create-request.json"),
                ReportDefinitionDto.class);
    assertThat(reportDefinitionDto.isCombined()).isFalse();
    assertThat(reportDefinitionDto.getReportType()).isEqualTo(ReportType.PROCESS);
    assertThat(reportDefinitionDto)
        .isInstanceOf(SingleProcessReportDefinitionRequestDto.class)
        .satisfies(
            processDefinition -> {
              assertThat(processDefinition.getData()).isNotNull();
            });
  }

  @Test
  public void testCanDeserializeToSingleProcessReport() throws Exception {
    final SingleProcessReportDefinitionRequestDto reportDefinitionDto =
        createOptimizeMapper()
            .readValue(
                ObjectMapperFactoryTest.class.getResourceAsStream(
                    "/test/data/single-process-report-definition-create-request.json"),
                SingleProcessReportDefinitionRequestDto.class);

    assertThat(reportDefinitionDto.isCombined()).isFalse();
    assertThat(reportDefinitionDto.getReportType()).isEqualTo(ReportType.PROCESS);
    assertThat(reportDefinitionDto).isInstanceOf(SingleProcessReportDefinitionRequestDto.class);
  }

  @Test
  public void testCanDeserializeToSingleDecisionReport() throws Exception {
    final SingleDecisionReportDefinitionRequestDto reportDefinitionDto =
        createOptimizeMapper()
            .readValue(
                ObjectMapperFactoryTest.class.getResourceAsStream(
                    "/test/data/single-decision-report-definition-create-request.json"),
                SingleDecisionReportDefinitionRequestDto.class);

    assertThat(reportDefinitionDto.isCombined()).isFalse();
    assertThat(reportDefinitionDto.getReportType()).isEqualTo(ReportType.DECISION);
    assertThat(reportDefinitionDto).isInstanceOf(SingleDecisionReportDefinitionRequestDto.class);
  }

  @Test
  public void testCanDeserializeToCombinedProcessReport() throws Exception {
    final CombinedReportDefinitionRequestDto reportDefinitionDto =
        createOptimizeMapper()
            .readValue(
                ObjectMapperFactoryTest.class.getResourceAsStream(
                    "/test/data/combined-process-report-definition-create-request.json"),
                CombinedReportDefinitionRequestDto.class);

    assertThat(reportDefinitionDto.isCombined()).isTrue();
    assertThat(reportDefinitionDto.getReportType()).isEqualTo(ReportType.PROCESS);
    assertThat(reportDefinitionDto).isInstanceOf(CombinedReportDefinitionRequestDto.class);
  }

  @Test
  public void testFilterSerialization() throws Exception {
    ProcessReportDataDto data =
        createOptimizeMapper()
            .readValue(
                ObjectMapperFactoryTest.class.getResourceAsStream("/test/data/filter_request.json"),
                ProcessReportDataDto.class);
    assertThat(
            ((BooleanVariableFilterDataDto) data.getFilter().get(0).getData())
                .getData()
                .getValues())
        .containsExactly(true);

    data =
        createOptimizeMapper()
            .readValue(
                ObjectMapperFactoryTest.class.getResourceAsStream(
                    "/test/data/filter_request_single.json"),
                ProcessReportDataDto.class);
    assertThat(
            ((BooleanVariableFilterDataDto) data.getFilter().get(0).getData())
                .getData()
                .getValues())
        .containsExactly(true);
  }

  @Test
  public void testHasAgentInstancesFilterSerialization() throws Exception {
    final ProcessReportDataDto data =
        createOptimizeMapper()
            .readValue(
                ObjectMapperFactoryTest.class.getResourceAsStream(
                    "/test/data/filter_request_has_agent_instances.json"),
                ProcessReportDataDto.class);

    assertThat(data.getFilter()).hasSize(1);
    assertThat(data.getFilter().get(0)).isInstanceOf(HasAgentInstancesFilterDto.class);
    assertThat(data.getFilter().get(0).getData())
        .isInstanceOf(HasAgentInstancesFilterDataDto.class);
  }

  @Test
  public void testAgentViewSerialization() throws Exception {
    final ProcessReportDataDto data =
        createOptimizeMapper()
            .readValue(
                ObjectMapperFactoryTest.class.getResourceAsStream(
                    "/test/data/report_request_agent_input_tokens.json"),
                ProcessReportDataDto.class);

    assertThat(data.getView().getEntity()).isEqualTo(ProcessViewEntity.AGENT_INSTANCE);
    assertThat(data.getView().getFirstProperty()).isEqualTo(ViewProperty.INPUT_TOKENS);
    assertThat(
            Arrays.stream(ProcessExecutionPlan.values()).map(ProcessExecutionPlan::getCommandKey))
        .contains(data.createCommandKey());
  }

  @Test
  public void testAgentAvgTokensPerCallViewSerialization() throws Exception {
    final ProcessReportDataDto data =
        createOptimizeMapper()
            .readValue(
                ObjectMapperFactoryTest.class.getResourceAsStream(
                    "/test/data/report_request_agent_avg_tokens_per_call.json"),
                ProcessReportDataDto.class);

    assertThat(data.getView().getEntity()).isEqualTo(ProcessViewEntity.AGENT_INSTANCE);
    assertThat(data.getView().getFirstProperty()).isEqualTo(ViewProperty.AVG_TOKENS_PER_CALL);
    assertThat(
            Arrays.stream(ProcessExecutionPlan.values()).map(ProcessExecutionPlan::getCommandKey))
        .contains(data.createCommandKey());
  }

  @Test
  public void testProcessDefinitionKeyGroupBySerialization() throws Exception {
    final ProcessReportDataDto data =
        createOptimizeMapper()
            .readValue(
                ObjectMapperFactoryTest.class.getResourceAsStream(
                    "/test/data/report_request_group_by_process_definition_key.json"),
                ProcessReportDataDto.class);

    assertThat(data.getGroupBy().getType()).isEqualTo(ProcessGroupByType.PROCESS_DEFINITION_KEY);
    assertThat(
            Arrays.stream(ProcessExecutionPlan.values()).map(ProcessExecutionPlan::getCommandKey))
        .contains(data.createCommandKey());
  }

  @Test
  public void testProcessDefinitionVersionGroupBySerialization() throws Exception {
    final ProcessReportDataDto data =
        createOptimizeMapper()
            .readValue(
                ObjectMapperFactoryTest.class.getResourceAsStream(
                    "/test/data/report_request_group_by_process_definition_version.json"),
                ProcessReportDataDto.class);

    assertThat(data.getGroupBy().getType())
        .isEqualTo(ProcessGroupByType.PROCESS_DEFINITION_VERSION);
    assertThat(
            Arrays.stream(ProcessExecutionPlan.values()).map(ProcessExecutionPlan::getCommandKey))
        .contains(data.createCommandKey());
  }

  @Test
  public void testFilterSerializationWithLowercaseType() throws Exception {
    ProcessReportDataDto data =
        createOptimizeMapper()
            .readValue(
                ObjectMapperFactoryTest.class.getResourceAsStream(
                    "/test/data/filter_request_lowercase_type.json"),
                ProcessReportDataDto.class);
    assertThat(
            ((BooleanVariableFilterDataDto) data.getFilter().get(0).getData())
                .getData()
                .getValues())
        .containsExactly(true);

    data =
        createOptimizeMapper()
            .readValue(
                ObjectMapperFactoryTest.class.getResourceAsStream(
                    "/test/data/filter_request_single.json"),
                ProcessReportDataDto.class);
    assertThat(
            ((BooleanVariableFilterDataDto) data.getFilter().get(0).getData())
                .getData()
                .getValues())
        .containsExactly(true);
  }

  @Test
  public void testOptimizeMapperDateSerialization() throws Exception {
    // given
    final String dateString = "2017-12-11T17:28:38.222+0100";

    // when
    final DateHolder instance = new DateHolder();
    instance.setDate(OffsetDateTime.parse(dateString, createOptimizeDateFormatter()));
    final String parsedString = createOptimizeMapper().writeValueAsString(instance);

    // then
    assertThat(parsedString).contains(dateString);
  }

  @Test
  public void testOptimizeMapperDateDeserialization() throws Exception {
    // given
    final String dateString = "2017-12-11T17:28:38.222+0100";
    final OffsetDateTime expectedOffsetDateTime =
        OffsetDateTime.parse(dateString, createOptimizeDateFormatter());

    // when
    final OffsetDateTime parsedOffsetDateTime =
        createOptimizeMapper()
            .readValue(createDateHolderJsonString(dateString), DateHolder.class)
            .getDate();

    // then
    assertThat(parsedOffsetDateTime).isEqualTo(expectedOffsetDateTime);
  }

  private DateTimeFormatter createOptimizeDateFormatter() {
    return new OptimizeDateTimeFormatterFactory().getObject();
  }

  private ObjectMapper createOptimizeMapper() {
    return objectMapperFactory.createOptimizeMapper();
  }

  private String createDateHolderJsonString(final String dateString) {
    return "{\"date\": \"" + dateString + "\"}";
  }

  static class DateHolder {

    private OffsetDateTime date;

    public OffsetDateTime getDate() {
      return date.truncatedTo(ChronoUnit.MILLIS);
    }

    public void setDate(final OffsetDateTime date) {
      this.date = date;
    }
  }
}

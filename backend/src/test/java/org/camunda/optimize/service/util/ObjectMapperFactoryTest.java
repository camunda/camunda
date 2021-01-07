/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.BooleanVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.camunda.optimize.service.util.mapper.ObjectMapperFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class ObjectMapperFactoryTest {

  private ObjectMapper optimizeMapper;
  private ObjectMapper engineMapper;

  @BeforeEach
  public void init() {
    ConfigurationService configurationService = ConfigurationServiceBuilder
      .createConfiguration()
      .loadConfigurationFrom("service-config.yaml")
      .build();
    OptimizeDateTimeFormatterFactory optimizeDateTimeFormatterFactory = new OptimizeDateTimeFormatterFactory();
    ObjectMapperFactory objectMapperFactory = new ObjectMapperFactory(
      optimizeDateTimeFormatterFactory.getObject(), configurationService
    );
    optimizeMapper = optimizeMapper == null ? objectMapperFactory.createOptimizeMapper() : optimizeMapper;
    engineMapper = engineMapper == null ? objectMapperFactory.createEngineMapper() : engineMapper;
  }

  /**
   * By default jackson fails if the external type id property is present but the actual property not.
   * In this case "reportType" is the external type id and "data" is the property.
   *
   * @see com.fasterxml.jackson.databind.DeserializationFeature#FAIL_ON_MISSING_EXTERNAL_TYPE_ID_PROPERTY
   */
  @Test
  public void testNoFailOnMissingReportDataAlthoughReportTypeSet() throws Exception {
    final ReportDefinitionDto reportDefinitionDto = optimizeMapper.readValue(
      getClass().getResourceAsStream("/test/data/single-process-report-definition-create-request.json"),
      ReportDefinitionDto.class
    );
    assertThat(reportDefinitionDto.isCombined()).isFalse();
    assertThat(reportDefinitionDto.getReportType()).isEqualTo(ReportType.PROCESS);
    assertThat(reportDefinitionDto)
      .isInstanceOf(SingleProcessReportDefinitionRequestDto.class)
      .satisfies(processDefinition -> {
        assertThat(processDefinition.getData()).isNotNull();
      });
  }

  @Test
  public void testCanDeserializeToSingleProcessReport() throws Exception {
    final SingleProcessReportDefinitionRequestDto reportDefinitionDto = optimizeMapper.readValue(
      getClass().getResourceAsStream("/test/data/single-process-report-definition-create-request.json"),
      SingleProcessReportDefinitionRequestDto.class
    );

    assertThat(reportDefinitionDto.isCombined()).isFalse();
    assertThat(reportDefinitionDto.getReportType()).isEqualTo(ReportType.PROCESS);
    assertThat(reportDefinitionDto)
      .isInstanceOf(SingleProcessReportDefinitionRequestDto.class);
  }

  @Test
  public void testCanDeserializeToSingleDecisionReport() throws Exception {
    final SingleDecisionReportDefinitionRequestDto reportDefinitionDto = optimizeMapper.readValue(
      getClass().getResourceAsStream("/test/data/single-decision-report-definition-create-request.json"),
      SingleDecisionReportDefinitionRequestDto.class
    );

    assertThat(reportDefinitionDto.isCombined()).isFalse();
    assertThat(reportDefinitionDto.getReportType()).isEqualTo(ReportType.DECISION);
    assertThat(reportDefinitionDto)
      .isInstanceOf(SingleDecisionReportDefinitionRequestDto.class);
  }

  @Test
  public void testCanDeserializeToCombinedProcessReport() throws Exception {
    final CombinedReportDefinitionRequestDto reportDefinitionDto = optimizeMapper.readValue(
      getClass().getResourceAsStream("/test/data/combined-process-report-definition-create-request.json"),
      CombinedReportDefinitionRequestDto.class
    );

    assertThat(reportDefinitionDto.isCombined()).isTrue();
    assertThat(reportDefinitionDto.getReportType()).isEqualTo(ReportType.PROCESS);
    assertThat(reportDefinitionDto)
      .isInstanceOf(CombinedReportDefinitionRequestDto.class);
  }

  @Test
  public void testFilterSerialization() throws Exception {
    ProcessReportDataDto data = optimizeMapper.readValue(
      this.getClass()
        .getResourceAsStream("/test/data/filter_request.json"),
      ProcessReportDataDto.class
    );
    assertThat(((BooleanVariableFilterDataDto) data.getFilter().get(0).getData()).getData().getValues())
      .containsExactly(true);

    data = optimizeMapper.readValue(
      this.getClass().getResourceAsStream("/test/data/filter_request_single.json"),
      ProcessReportDataDto.class
    );
    assertThat(((BooleanVariableFilterDataDto) data.getFilter().get(0).getData()).getData().getValues())
      .containsExactly(true);
  }


  @Test
  public void testFilterSerializationWithLowercaseType() throws Exception {
    ProcessReportDataDto data = optimizeMapper.readValue(
      this.getClass()
        .getResourceAsStream("/test/data/filter_request_lowercase_type.json"),
      ProcessReportDataDto.class
    );
    assertThat(((BooleanVariableFilterDataDto) data.getFilter().get(0).getData()).getData().getValues())
      .containsExactly(true);

    data = optimizeMapper.readValue(
      this.getClass().getResourceAsStream("/test/data/filter_request_single.json"),
      ProcessReportDataDto.class
    );
    assertThat(((BooleanVariableFilterDataDto) data.getFilter().get(0).getData()).getData().getValues())
      .containsExactly(true);
  }

  @Test
  public void testDateSerialization() throws Exception {
    DateHolder instance = new DateHolder();
    instance.setDate(OffsetDateTime.now());
    String parsedString = optimizeMapper.writeValueAsString(instance);
    assertThat(parsedString).isNotNull();

    DateHolder result = optimizeMapper.readValue(
      parsedString,
      DateHolder.class
    );
    assertThat(result.getDate()).isEqualTo(instance.getDate());
  }

  @Test
  public void testFromString() throws JsonProcessingException {
    String value = "2017-12-11T17:28:38.222+0100";
    DateHolder toTest = new DateHolder();
    toTest.setDate(OffsetDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")));

    assertThat(optimizeMapper.writeValueAsString(toTest)).contains(value);
  }

  @Test
  public void testFromStringWithOldEngineFormat() throws JsonProcessingException {
    String value = "2017-12-11T17:28:38";
    DateHolder toTest = new DateHolder();
    toTest.setDate(
      ZonedDateTime
        .parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneId.systemDefault()))
        .toOffsetDateTime()
    );

    assertThat(optimizeMapper.writeValueAsString(toTest)).contains(value);
  }

  @Test
  public void testEngineDateSerialization() throws Exception {
    DateHolder instance = new DateHolder();
    instance.setDate(OffsetDateTime.now());
    String parsedString = engineMapper.writeValueAsString(instance);
    assertThat(parsedString).isNotNull();

    DateHolder result = engineMapper.readValue(
      parsedString,
      DateHolder.class
    );
    assertThat(result.getDate()).isEqualTo(instance.getDate());
  }

  @Test
  public void testEngineDateFromString() throws JsonProcessingException {
    String value = "2017-12-11T17:28:38.222+0100";
    DateHolder toTest = new DateHolder();
    toTest.setDate(OffsetDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")));

    assertThat(engineMapper.writeValueAsString(toTest)).contains(value);
  }

  @Test
  public void testEngineDateFromStringWithOldEngineFormat() throws JsonProcessingException {
    String value = "2017-12-11T17:28:38";
    DateHolder toTest = new DateHolder();
    toTest.setDate(
      ZonedDateTime
        .parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneId.systemDefault()))
        .toOffsetDateTime()
    );

    assertThat(engineMapper.writeValueAsString(toTest)).contains(value);
  }


  static class DateHolder {
    private OffsetDateTime date;

    public OffsetDateTime getDate() {
      return date.truncatedTo(ChronoUnit.MILLIS);
    }

    public void setDate(OffsetDateTime date) {
      this.date = date;
    }
  }
}

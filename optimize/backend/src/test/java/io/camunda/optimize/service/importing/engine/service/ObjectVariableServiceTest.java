/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service;

import static io.camunda.optimize.dto.optimize.ReportConstants.STRING_TYPE;
import static io.camunda.optimize.dto.optimize.query.variable.VariableType.DATE;
import static io.camunda.optimize.dto.optimize.query.variable.VariableType.STRING;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableUpdateDto;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectVariableServiceTest {

  private final ObjectVariableService objectVariableService = new ObjectVariableService();

  @ParameterizedTest
  @ValueSource(strings = {"GB", "NZ", "AM", "PM", "US", "CA", "DE", "FR"})
  void countryCodesShouldRemainAsStringsNotBeParsedAsDates(final String countryCode) {
    // given
    final ProcessVariableUpdateDto variable = new ProcessVariableUpdateDto();
    variable.setId("1");
    variable.setName("COUNTRY_CODE");
    variable.setType(STRING_TYPE);
    variable.setValue(countryCode);

    // when
    final List<ProcessVariableDto> result =
        objectVariableService.convertToProcessVariableDtos(Collections.singletonList(variable));

    // then
    assertThat(result).hasSize(1);
    final ProcessVariableDto resultVariable = result.get(0);
    assertThat(resultVariable.getType())
        .as("Country code %s should be STRING not DATE", countryCode)
        .isEqualTo(STRING.getId());
    assertThat(resultVariable.getValue()).containsExactly(countryCode);
  }

  @ParameterizedTest
  @ValueSource(strings = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Sat", "Sun"})
  void weekdayNamesShouldRemainAsStringsNotBeParsedAsDates(final String weekday) {
    // given
    final ProcessVariableUpdateDto variable = new ProcessVariableUpdateDto();
    variable.setId("1");
    variable.setName("WEEKDAY");
    variable.setType(STRING_TYPE);
    variable.setValue(weekday);

    // when
    final List<ProcessVariableDto> result =
        objectVariableService.convertToProcessVariableDtos(Collections.singletonList(variable));

    // then
    assertThat(result).hasSize(1);
    final ProcessVariableDto resultVariable = result.get(0);
    assertThat(resultVariable.getType())
        .as("Weekday name %s should be STRING not DATE", weekday)
        .isEqualTo(STRING.getId());
    assertThat(resultVariable.getValue()).containsExactly(weekday);
  }

  @Test
  void validDateStringShouldBeParsedAsDate() {
    // given
    final ProcessVariableUpdateDto variable = new ProcessVariableUpdateDto();
    variable.setId("1");
    variable.setName("birthDate");
    variable.setType(STRING_TYPE);
    variable.setValue("2025-01-15T10:30:00Z");

    // when
    final List<ProcessVariableDto> result =
        objectVariableService.convertToProcessVariableDtos(Collections.singletonList(variable));

    // then
    assertThat(result).hasSize(1);
    final ProcessVariableDto resultVariable = result.get(0);
    assertThat(resultVariable.getType()).isEqualTo(DATE.getId());
    assertThat(resultVariable.getValue()).isNotEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"AM", "am", "PM", "pm"})
  void meridiemIndicatorShouldNotBeParsedAsDate(final String meridiem) {
    // given
    final ProcessVariableUpdateDto variable = new ProcessVariableUpdateDto();
    variable.setId("1");
    variable.setName("time_period");
    variable.setType(STRING_TYPE);
    variable.setValue(meridiem);

    // when
    final List<ProcessVariableDto> result =
        objectVariableService.convertToProcessVariableDtos(Collections.singletonList(variable));

    // then
    assertThat(result).hasSize(1);
    final ProcessVariableDto resultVariable = result.get(0);
    assertThat(resultVariable.getType())
        .as("%s should be treated as STRING, not as a time indicator", meridiem)
        .isEqualTo(STRING.getId());
    assertThat(resultVariable.getValue()).containsExactly(meridiem);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.junit.jupiter.api.Test;

public class DecisionVariableHelperTest {

  @Test
  void shouldGetVariableValueFieldReturnCorrectFieldPath() {
    // given
    final String variablePath = "testPath";

    // when
    final String result = DecisionVariableHelper.getVariableValueField(variablePath);

    // then
    assertThat(result).isEqualTo(variablePath + ".value");
  }

  @Test
  void shouldGetVariableMultivalueFieldsReturnUnmodifiableList() {
    // given

    // when
    final var result = DecisionVariableHelper.getVariableMultivalueFields();

    // then
    assertThat(result)
        .containsExactly(VariableType.DATE, VariableType.DOUBLE, VariableType.LONG)
        .isUnmodifiable();
  }

  @Test
  void shouldGetVariableStringValueFieldReturnCorrectStringValueField() {
    // given
    final String variablePath = "testPath";

    // when
    final String result = DecisionVariableHelper.getVariableStringValueField(variablePath);

    // then
    assertThat(result).isEqualTo(variablePath + ".value");
  }

  @Test
  void shouldGetValueSearchFieldReturnSearchFieldPath() {
    // given
    final String variablePath = "testPath";
    final String searchFieldName = "field";

    // when
    final String result = DecisionVariableHelper.getValueSearchField(variablePath, searchFieldName);

    // then
    assertThat(result).isEqualTo(variablePath + ".value.field");
  }

  @Test
  void shouldBuildWildcardQueryReturnWildcardString() {
    // given
    final String valueFilter = "query";

    // when
    final String result = DecisionVariableHelper.buildWildcardQuery(valueFilter);

    // then
    assertThat(result).isEqualTo("*query*");
  }

  @Test
  void shouldGetVariableValueFieldForTypeHandleStringType() {
    // given
    final String variablePath = "testPath";
    final VariableType type = VariableType.STRING;

    // when
    final String result = DecisionVariableHelper.getVariableValueFieldForType(variablePath, type);

    // then
    assertThat(result).isEqualTo(variablePath + ".value");
  }

  @Test
  void shouldGetVariableValueFieldForTypeHandleDateType() {
    // given
    final String variablePath = "testPath";
    final VariableType type = VariableType.DATE;

    // when
    final String result = DecisionVariableHelper.getVariableValueFieldForType(variablePath, type);

    // then
    assertThat(result).isEqualTo(variablePath + ".value.date");
  }

  @Test
  void shouldGetVariableValueFieldForTypeThrowExceptionForNullType() {
    // given
    final String variablePath = "testPath";
    final VariableType type = null;

    // then
    assertThatThrownBy(
            () -> DecisionVariableHelper.getVariableValueFieldForType(variablePath, type))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("No Type provided");
  }

  @Test
  void shouldGetVariableClauseIdFieldReturnCorrectFieldPath() {
    // given
    final String variablePath = "testPath";

    // when
    final String result = DecisionVariableHelper.getVariableClauseIdField(variablePath);

    // then
    assertThat(result).isEqualTo(variablePath + ".clauseId");
  }

  @Test
  void shouldGetVariableTypeFieldReturnCorrectFieldPath() {
    // given
    final String variablePath = "testPath";

    // when
    final String result = DecisionVariableHelper.getVariableTypeField(variablePath);

    // then
    assertThat(result).isEqualTo(variablePath + ".type");
  }
}

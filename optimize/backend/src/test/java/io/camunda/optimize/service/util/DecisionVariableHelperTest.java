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
    String variablePath = "testPath";

    // when
    String result = DecisionVariableHelper.getVariableValueField(variablePath);

    // then
    assertThat(result).isEqualTo("testPath.value");
  }

  @Test
  void shouldGetVariableMultivalueFieldsReturnUnmodifiableList() {
    // given

    // when
    var result = DecisionVariableHelper.getVariableMultivalueFields();

    // then
    assertThat(result)
        .containsExactly(VariableType.DATE, VariableType.DOUBLE, VariableType.LONG)
        .isUnmodifiable();
  }

  @Test
  void shouldGetVariableStringValueFieldReturnCorrectStringValueField() {
    // given
    String variablePath = "testPath";

    // when
    String result = DecisionVariableHelper.getVariableStringValueField(variablePath);

    // then
    assertThat(result).isEqualTo("testPath.value");
  }

  @Test
  void shouldGetValueSearchFieldReturnSearchFieldPath() {
    // given
    String variablePath = "testPath";
    String searchFieldName = "field";

    // when
    String result = DecisionVariableHelper.getValueSearchField(variablePath, searchFieldName);

    // then
    assertThat(result).isEqualTo("testPath.value.field");
  }

  @Test
  void shouldBuildWildcardQueryReturnWildcardString() {
    // given
    String valueFilter = "query";

    // when
    String result = DecisionVariableHelper.buildWildcardQuery(valueFilter);

    // then
    assertThat(result).isEqualTo("*query*");
  }

  @Test
  void shouldGetVariableValueFieldForTypeHandleStringType() {
    // given
    String variablePath = "testPath";
    VariableType type = VariableType.STRING;

    // when
    String result = DecisionVariableHelper.getVariableValueFieldForType(variablePath, type);

    // then
    assertThat(result).isEqualTo("testPath.value");
  }

  @Test
  void shouldGetVariableValueFieldForTypeHandleDateType() {
    // given
    String variablePath = "testPath";
    VariableType type = VariableType.DATE;

    // when
    String result = DecisionVariableHelper.getVariableValueFieldForType(variablePath, type);

    // then
    assertThat(result).isEqualTo("testPath.value.date");
  }

  @Test
  void shouldGetVariableValueFieldForTypeThrowExceptionForNullType() {
    // given
    String variablePath = "testPath";
    VariableType type = null;

    // then
    assertThatThrownBy(
            () -> DecisionVariableHelper.getVariableValueFieldForType(variablePath, type))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("No Type provided");
  }

  @Test
  void shouldGetVariableClauseIdFieldReturnCorrectFieldPath() {
    // given
    String variablePath = "testPath";

    // when
    String result = DecisionVariableHelper.getVariableClauseIdField(variablePath);

    // then
    assertThat(result).isEqualTo("testPath.clauseId");
  }

  @Test
  void shouldGetVariableTypeFieldReturnCorrectFieldPath() {
    // given
    String variablePath = "testPath";

    // when
    String result = DecisionVariableHelper.getVariableTypeField(variablePath);

    // then
    assertThat(result).isEqualTo("testPath.type");
  }
}

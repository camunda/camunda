/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.ValueTypeEnum;
import org.junit.jupiter.api.Test;

public class VariableDbModelTest {

  @Test
  public void shouldSetCorrectTypeAndValueForLongValue() {
    // given
    final VariableDbModel.VariableDbModelBuilder builder =
        new VariableDbModel.VariableDbModelBuilder();

    // when
    final VariableDbModel model =
        builder
            .key(1L)
            .name("test")
            .value("123456")
            .scopeKey(2L)
            .processInstanceKey(3L)
            .tenantId("tenant1")
            .build();

    // then
    assertThat(model.key()).isEqualTo(1L);
    assertThat(model.name()).isEqualTo("test");
    assertThat(model.type()).isEqualTo(ValueTypeEnum.LONG);
    assertThat(model.doubleValue()).isNull();
    assertThat(model.longValue()).isEqualTo(123456L);
    assertThat(model.value()).isEqualTo("123456");
    assertThat(model.fullValue()).isNull();
    assertThat(model.isPreview()).isFalse();
    assertThat(model.scopeKey()).isEqualTo(2L);
    assertThat(model.processInstanceKey()).isEqualTo(3L);
    assertThat(model.tenantId()).isEqualTo("tenant1");
  }

  @Test
  public void shouldSetCorrectTypeAndValueForNegativeLongValue() {
    // given
    final VariableDbModel.VariableDbModelBuilder builder =
        new VariableDbModel.VariableDbModelBuilder();

    // when
    final VariableDbModel model =
        builder
            .key(1L)
            .name("test")
            .value("-123456")
            .scopeKey(2L)
            .processInstanceKey(3L)
            .tenantId("tenant1")
            .build();

    // then
    assertThat(model.key()).isEqualTo(1L);
    assertThat(model.name()).isEqualTo("test");
    assertThat(model.type()).isEqualTo(ValueTypeEnum.LONG);
    assertThat(model.doubleValue()).isNull();
    assertThat(model.longValue()).isEqualTo(-123456L);
    assertThat(model.value()).isEqualTo("-123456");
    assertThat(model.fullValue()).isNull();
    assertThat(model.isPreview()).isFalse();
    assertThat(model.scopeKey()).isEqualTo(2L);
    assertThat(model.processInstanceKey()).isEqualTo(3L);
    assertThat(model.tenantId()).isEqualTo("tenant1");
  }

  @Test
  public void shouldSetCorrectTypeAndValueForDoubleValue() {
    // given
    final VariableDbModel.VariableDbModelBuilder builder =
        new VariableDbModel.VariableDbModelBuilder();

    // when
    final VariableDbModel model =
        builder
            .key(1L)
            .name("test")
            .value("123.456")
            .scopeKey(2L)
            .processInstanceKey(3L)
            .tenantId("tenant1")
            .build();

    // then
    assertThat(model.key()).isEqualTo(1L);
    assertThat(model.name()).isEqualTo("test");
    assertThat(model.type()).isEqualTo(ValueTypeEnum.DOUBLE);
    assertThat(model.doubleValue()).isEqualTo(123.456);
    assertThat(model.longValue()).isNull();
    assertThat(model.value()).isEqualTo("123.456");
    assertThat(model.fullValue()).isNull();
    assertThat(model.isPreview()).isFalse();
    assertThat(model.scopeKey()).isEqualTo(2L);
    assertThat(model.processInstanceKey()).isEqualTo(3L);
    assertThat(model.tenantId()).isEqualTo("tenant1");
  }

  @Test
  public void shouldSetCorrectTypeAndValueForNegativeDoubleValue() {
    // given
    final VariableDbModel.VariableDbModelBuilder builder =
        new VariableDbModel.VariableDbModelBuilder();

    // when
    final VariableDbModel model =
        builder
            .key(1L)
            .name("test")
            .value("-123.456")
            .scopeKey(2L)
            .processInstanceKey(3L)
            .tenantId("tenant1")
            .build();

    // then
    assertThat(model.key()).isEqualTo(1L);
    assertThat(model.name()).isEqualTo("test");
    assertThat(model.type()).isEqualTo(ValueTypeEnum.DOUBLE);
    assertThat(model.doubleValue()).isEqualTo(-123.456);
    assertThat(model.longValue()).isNull();
    assertThat(model.value()).isEqualTo("-123.456");
    assertThat(model.fullValue()).isNull();
    assertThat(model.isPreview()).isFalse();
    assertThat(model.scopeKey()).isEqualTo(2L);
    assertThat(model.processInstanceKey()).isEqualTo(3L);
    assertThat(model.tenantId()).isEqualTo("tenant1");
  }

  @Test
  public void shouldSetCorrectTypeAndValueForNonNumericValue() {
    // given
    final VariableDbModel.VariableDbModelBuilder builder =
        new VariableDbModel.VariableDbModelBuilder();

    // when
    final VariableDbModel model =
        builder
            .key(1L)
            .name("test")
            .value("non-numeric")
            .scopeKey(2L)
            .processInstanceKey(3L)
            .tenantId("tenant1")
            .build();

    // then
    assertThat(model.key()).isEqualTo(1L);
    assertThat(model.name()).isEqualTo("test");
    assertThat(model.type()).isEqualTo(ValueTypeEnum.STRING);
    assertThat(model.doubleValue()).isNull();
    assertThat(model.longValue()).isNull();
    assertThat(model.value()).isEqualTo("non-numeric");
    assertThat(model.fullValue()).isNull();
    assertThat(model.isPreview()).isFalse();
    assertThat(model.scopeKey()).isEqualTo(2L);
    assertThat(model.processInstanceKey()).isEqualTo(3L);
    assertThat(model.tenantId()).isEqualTo("tenant1");
  }

  @Test
  public void shouldSetCorrectTypeAndValueForLargeValue() {
    // given
    final String largeValue = "a".repeat(VariableDbModel.DEFAULT_VARIABLE_SIZE_THRESHOLD + 1);
    final VariableDbModel.VariableDbModelBuilder builder =
        new VariableDbModel.VariableDbModelBuilder();

    // when
    final VariableDbModel model =
        builder
            .key(1L)
            .name("test")
            .value(largeValue)
            .scopeKey(2L)
            .processInstanceKey(3L)
            .tenantId("tenant1")
            .build();

    // then
    assertThat(model.key()).isEqualTo(1L);
    assertThat(model.name()).isEqualTo("test");
    assertThat(model.type()).isEqualTo(ValueTypeEnum.STRING);
    assertThat(model.doubleValue()).isNull();
    assertThat(model.longValue()).isNull();
    assertThat(model.value())
        .isEqualTo(largeValue.substring(0, VariableDbModel.DEFAULT_VARIABLE_SIZE_THRESHOLD));
    assertThat(model.fullValue()).isEqualTo(largeValue);
    assertThat(model.isPreview()).isTrue();
    assertThat(model.scopeKey()).isEqualTo(2L);
    assertThat(model.processInstanceKey()).isEqualTo(3L);
    assertThat(model.tenantId()).isEqualTo("tenant1");
  }
}

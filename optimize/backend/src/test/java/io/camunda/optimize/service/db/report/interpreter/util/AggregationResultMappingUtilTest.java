/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AggregationResultMappingUtilTest {

  @Test
  void shouldMapNullMetricValueToNull() {
    // given an empty bucket, which the Elasticsearch 9 client reports as a null metric value
    // when
    final Double result = AggregationResultMappingUtil.mapToDoubleOrNull(null);

    // then
    assertThat(result).isNull();
  }

  @Test
  void shouldMapInfiniteAndNaNToNull() {
    assertThat(AggregationResultMappingUtil.mapToDoubleOrNull(Double.POSITIVE_INFINITY)).isNull();
    assertThat(AggregationResultMappingUtil.mapToDoubleOrNull(Double.NaN)).isNull();
  }

  @Test
  void shouldKeepFiniteValue() {
    assertThat(AggregationResultMappingUtil.mapToDoubleOrNull(4.2)).isEqualTo(4.2);
  }
}

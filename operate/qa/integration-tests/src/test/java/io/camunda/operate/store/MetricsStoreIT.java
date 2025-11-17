/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store;

import static io.camunda.operate.store.MetricsStore.EVENT_DECISION_INSTANCE_EVALUATED;
import static io.camunda.operate.store.MetricsStore.EVENT_PROCESS_INSTANCE_FINISHED;
import static io.camunda.operate.store.MetricsStore.EVENT_PROCESS_INSTANCE_STARTED;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.entities.MetricEntity;
import io.camunda.operate.entities.OperateEntity;
import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.SearchTestRule;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class MetricsStoreIT extends OperateAbstractIT {

  @Rule public SearchTestRule searchTestRule = new SearchTestRule();
  @Autowired private MetricsStore metricsStore;

  @Test
  public void testRetrieveProcessInstanceCount() {
    // given
    final OffsetDateTime startTime = OffsetDateTime.now().minusHours(1);
    final OffsetDateTime endTime = OffsetDateTime.now();

    // Create process instance metrics
    createProcessInstanceMetrics(5, startTime.plusMinutes(15));
    createProcessInstanceMetrics(2, startTime.plusMinutes(30));
    searchTestRule.refreshOperateSearchIndices();

    // when
    final Long count = metricsStore.retrieveProcessInstanceCount(startTime, endTime);

    // then
    assertThat(count).isEqualTo(7L);
  }

  @Test
  public void testRetrieveDecisionInstanceCount() {
    // given
    final OffsetDateTime startTime = OffsetDateTime.now().minusHours(1);
    final OffsetDateTime endTime = OffsetDateTime.now();

    // Create decision instance metrics
    createDecisionInstanceMetrics(5, startTime.plusMinutes(15));
    createDecisionInstanceMetrics(3, startTime.plusMinutes(30));
    searchTestRule.refreshOperateSearchIndices();

    // when
    final Long count = metricsStore.retrieveDecisionInstanceCount(startTime, endTime);

    // then
    assertThat(count).isEqualTo(8L);
  }

  private void createProcessInstanceMetrics(final int count, final OffsetDateTime eventTime) {
    final List<OperateEntity> entities = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      final long epochSecond = eventTime.toEpochSecond();
      final String value = "process-instance-" + epochSecond + "-" + i;
      entities.add(new MetricEntity(EVENT_PROCESS_INSTANCE_STARTED, value, eventTime));
      entities.add(new MetricEntity(EVENT_PROCESS_INSTANCE_FINISHED, value, eventTime));
    }
    searchTestRule.persistNew(entities.toArray(new OperateEntity[0]));
  }

  private void createDecisionInstanceMetrics(final int count, final OffsetDateTime eventTime) {
    final List<OperateEntity> entities = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      final long epochSecond = eventTime.toEpochSecond();
      final String value = "process-instance-" + epochSecond + "-" + i;
      entities.add(new MetricEntity(EVENT_DECISION_INSTANCE_EVALUATED, value, eventTime));
    }
    searchTestRule.persistNew(entities.toArray(new OperateEntity[0]));
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.operate.Metrics;
import io.camunda.operate.entities.meta.ImportPositionEntity;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.store.ImportStore;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class ImportPositionHolderMetricTest {

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final OperateProperties operateProperties = new OperateProperties();
  private final ImportStore importStore = mock(ImportStore.class);
  private ImportPositionHolder holder;

  @Before
  public void setUp() {
    holder = new ImportPositionHolder();
    ReflectionTestUtils.setField(holder, "operateProperties", operateProperties);
    ReflectionTestUtils.setField(holder, "importStore", importStore);
    ReflectionTestUtils.setField(holder, "metrics", new Metrics(registry));
  }

  @Test
  public void shouldNotPublishGaugeWhenDisabled() {
    // when (disabled is the default)
    holder.recordLatestLoadedPosition(position("process-instance", 1, 250));

    // then
    assertNull(registry.find(Metrics.GAUGE_NAME_IMPORT_POSITION).gauge());
  }

  @Test
  public void shouldPublishHighestImportedPositionPerPartition() {
    // given
    operateProperties.getImporter().setImportPositionMetricEnabled(true);

    // when
    holder.recordLatestLoadedPosition(position("job", 1, 100));
    holder.recordLatestLoadedPosition(position("process-instance", 1, 250));
    holder.recordLatestLoadedPosition(position("variable", 1, 200));
    holder.recordLatestLoadedPosition(position("process-instance", 2, 70));

    // then
    assertEquals(250, gauge(1), 0);
    assertEquals(70, gauge(2), 0);
    assertEquals(2, registry.find(Metrics.GAUGE_NAME_IMPORT_POSITION).gauges().size());
  }

  @Test
  public void shouldNotDecreaseWhenAnotherTypeIsBehind() {
    // given
    operateProperties.getImporter().setImportPositionMetricEnabled(true);
    holder.recordLatestLoadedPosition(position("process-instance", 1, 250));

    // when
    holder.recordLatestLoadedPosition(position("job", 1, 120));

    // then
    assertEquals(250, gauge(1), 0);
  }

  @Test
  public void shouldStartFromPersistedPositionAfterRestart() throws Exception {
    // given
    operateProperties.getImporter().setImportPositionMetricEnabled(true);
    when(importStore.getImportPositionByAliasAndPartitionId("process-instance", 3))
        .thenReturn(position("process-instance", 3, 9_848_277));

    // when
    holder.getLatestScheduledPosition("process-instance", 3);

    // then
    assertEquals(9_848_277, gauge(3), 0);
  }

  @Test
  public void shouldKeepExactValueForLargePositions() {
    // given: log positions stay far below 2^53, so the double gauge value is exact
    operateProperties.getImporter().setImportPositionMetricEnabled(true);
    final long position = (1L << 52) + 1;

    // when
    holder.recordLatestLoadedPosition(position("process-instance", 1, position));

    // then
    assertEquals(position, (long) gauge(1));
  }

  private double gauge(final int partitionId) {
    final Gauge gauge =
        registry
            .find(Metrics.GAUGE_NAME_IMPORT_POSITION)
            .tag(Metrics.TAG_KEY_PARTITION, String.valueOf(partitionId))
            .gauge();
    return gauge.value();
  }

  private static ImportPositionEntity position(
      final String alias, final int partitionId, final long position) {
    return new ImportPositionEntity()
        .setAliasName(alias)
        .setPartitionId(partitionId)
        .setPosition(position);
  }
}

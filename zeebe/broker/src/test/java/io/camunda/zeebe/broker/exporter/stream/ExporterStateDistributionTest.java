/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;

public class ExporterStateDistributionTest {

  private ExporterStateDistributionService exporterStateDistributionService;
  private Map<String, ExporterStateDistributeMessage.ExporterStateEntry> exporterState;
  private SimplePartitionMessageService partitionMessagingService;

  @Before
  public void setup() {
    exporterState = new HashMap<>();
    partitionMessagingService = new SimplePartitionMessageService();
    exporterStateDistributionService =
        new ExporterStateDistributionService(
            exporterState::put, partitionMessagingService, "topic", List.of("topic", "foo"));
  }

  @Test
  public void shouldSubscribeForGivenTopics() {
    // given

    // when
    exporterStateDistributionService.subscribeForExporterState(Runnable::run);

    // then
    assertThat(partitionMessagingService.consumers).containsKey("topic");
    assertThat(partitionMessagingService.consumers).containsKey("foo");
  }

  @Test
  public void shouldConsumeExporterMessage() {
    // given
    final var metadata1 = BufferUtil.wrapString("e1");
    final var metadata2 = BufferUtil.wrapString("e2");

    final var exporterPositionsMessage = new ExporterStateDistributeMessage();
    exporterPositionsMessage.putExporter("elastic", 123, metadata1);
    exporterPositionsMessage.putExporter("metric", 345, metadata2);
    exporterStateDistributionService.subscribeForExporterState(Runnable::run);

    // when
    exporterStateDistributionService.distributeExporterState(exporterPositionsMessage);

    // then
    assertThat(exporterState)
        .containsEntry(
            "elastic", new ExporterStateDistributeMessage.ExporterStateEntry(123L, metadata1))
        .containsEntry(
            "metric", new ExporterStateDistributeMessage.ExporterStateEntry(345L, metadata2));
  }

  @Test
  public void shouldRemoveSubscriptionOnClose() throws Exception {
    // given
    final var exporterPositionsMessage = new ExporterStateDistributeMessage();
    exporterPositionsMessage.putExporter("elastic", 123, new UnsafeBuffer());
    exporterPositionsMessage.putExporter("metric", 345, new UnsafeBuffer());
    exporterStateDistributionService.subscribeForExporterState(Runnable::run);

    // when
    exporterStateDistributionService.close();

    // then
    assertThat(partitionMessagingService.consumers).isEmpty();
  }
}

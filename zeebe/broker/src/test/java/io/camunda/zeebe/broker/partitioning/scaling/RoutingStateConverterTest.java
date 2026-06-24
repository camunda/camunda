/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.scaling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.dynamic.config.state.RoutingState.MessageCorrelation;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.AllPartitions;
import io.camunda.zeebe.protocol.impl.record.value.scaling.ScaleRecord;
import java.util.List;
import org.junit.jupiter.api.Test;

public class RoutingStateConverterTest {

  @Test
  void shouldConvertAllPartitionsHandling() {
    final ScaleRecord record = new ScaleRecord();
    record.setDesiredPartitionCount(3);
    record.setRedistributedPartitions(List.of(1, 2, 3));
    record.setMessageCorrelationPartitions(2);

    final RoutingState state = RoutingStateConverter.fromScaleRecord(record);

    assertThat(state.requestHandling()).isInstanceOf(RequestHandling.AllPartitions.class);
    final var allPartitions = (RequestHandling.AllPartitions) state.requestHandling();
    assertThat(allPartitions).isEqualTo(new AllPartitions(3));
    assertThat(state.messageCorrelation()).isInstanceOf(MessageCorrelation.HashMod.class);
    assertThat(((MessageCorrelation.HashMod) state.messageCorrelation()).partitionCount())
        .isEqualTo(2);
  }

  @Test
  void shouldConvertActivePartitionsHandling() {
    final ScaleRecord record = new ScaleRecord();
    record.setDesiredPartitionCount(5);
    record.setRedistributedPartitions(List.of(1, 2, 3));
    record.setMessageCorrelationPartitions(3);

    final RoutingState state = RoutingStateConverter.fromScaleRecord(record);

    assertThat(state.requestHandling()).isInstanceOf(RequestHandling.ActivePartitions.class);
    final RequestHandling.ActivePartitions active =
        (RequestHandling.ActivePartitions) state.requestHandling();
    assertThat(active.activePartitions().size()).isEqualTo(3);
    assertThat(active.inactivePartitions()).containsExactlyInAnyOrder(4, 5);
    assertThat(state.messageCorrelation()).isInstanceOf(MessageCorrelation.HashMod.class);
    assertThat(((MessageCorrelation.HashMod) state.messageCorrelation()).partitionCount())
        .isEqualTo(3);
  }

  @Test
  void shouldRejectInvalidRecord() {
    final var scaleRecord = new ScaleRecord();

    assertThatThrownBy(() -> RoutingStateConverter.fromScaleRecord(scaleRecord))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("desired partition count must be greater than 0, was -1");

    scaleRecord.setDesiredPartitionCount(0);
    assertThatThrownBy(() -> RoutingStateConverter.fromScaleRecord(scaleRecord))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("desired partition count must be greater than 0, was 0");

    scaleRecord.setDesiredPartitionCount(1);
    scaleRecord.setMessageCorrelationPartitions(1);
    assertThatThrownBy(() -> RoutingStateConverter.fromScaleRecord(scaleRecord))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "partitions in message correlation cannot be more than partitions in request handling: 1 > 0");
  }
}

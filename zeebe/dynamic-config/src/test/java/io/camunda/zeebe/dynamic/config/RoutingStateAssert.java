/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.dynamic.config.state.RoutingState.MessageCorrelation.HashMod;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling;
import java.util.stream.IntStream;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;

public class RoutingStateAssert extends AbstractAssert<RoutingStateAssert, RoutingState> {

  protected RoutingStateAssert(final RoutingState routingState, final Class<?> selfType) {
    super(routingState, selfType);
  }

  public static RoutingStateAssert assertThat(final RoutingState routingState) {
    return new RoutingStateAssert(routingState, RoutingStateAssert.class);
  }

  public RoutingStateAssert hasVersion(final int version) {
    Assertions.assertThat(actual.version()).isEqualTo(version);
    return this;
  }

  public RoutingStateAssert hasActivatedPartitions(final int partitionCount) {
    Assertions.assertThat(actual.requestHandling().activePartitions())
        .containsExactlyElementsOf(IntStream.rangeClosed(1, partitionCount).boxed().toList());
    return this;
  }

  public RoutingStateAssert hasRequestHandling(final RequestHandling requestHandling) {
    Assertions.assertThat(actual.requestHandling()).isEqualTo(requestHandling);
    return this;
  }

  public RoutingStateAssert correlatesMessagesToPartitions(final int partitionCount) {
    Assertions.assertThat(actual.messageCorrelation()).isNotNull();
    Assertions.assertThat(actual.messageCorrelation()).isInstanceOf(HashMod.class);
    final var actualPartitionCount = ((HashMod) actual.messageCorrelation()).partitionCount();
    Assertions.assertThat(actualPartitionCount).isEqualTo(partitionCount);
    return this;
  }
}

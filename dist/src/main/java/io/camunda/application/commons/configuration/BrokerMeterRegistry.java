/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.configuration;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

public record BrokerMeterRegistry(MeterRegistry registry) {

  public static BrokerMeterRegistry create(
      final MeterRegistry meterRegistry, final MemberId memberId) {
    return new BrokerMeterRegistry(
        MicrometerUtil.wrap(meterRegistry, Tags.of("nodeId", memberId.toString())));
  }
}

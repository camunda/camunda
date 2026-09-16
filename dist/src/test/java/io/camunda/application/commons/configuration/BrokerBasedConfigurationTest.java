/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.zeebe.broker.system.configuration.partitioning.Scheme;
import io.camunda.zeebe.broker.system.configuration.partitioning.ZoneAwareCfg;
import io.camunda.zeebe.broker.system.configuration.partitioning.ZoneCfg;
import io.camunda.zeebe.dynamic.nodeid.NodeIdProvider;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.context.LifecycleProperties;

final class BrokerBasedConfigurationTest {

  @TempDir private Path tempDir;

  @ParameterizedTest(name = "{0}")
  @MethodSource("nodeIdConfigurations")
  void shouldWrapMeterRegistryWithResolvedNodeId(
      final String configurationName, final boolean zoned, final String expectedNodeId) {
    // given
    final var properties = new BrokerBasedProperties();
    if (zoned) {
      properties.getCluster().setZone("zone-a");
      properties.getExperimental().getPartitioning().setScheme(Scheme.ZONE_AWARE);
      properties
          .getExperimental()
          .getPartitioning()
          .setZoneAware(new ZoneAwareCfg(List.of(new ZoneCfg("zone-a", 3, 3, 1))));
    }
    final var meterRegistry = new SimpleMeterRegistry();
    final var configuration =
        new BrokerBasedConfiguration(
            new WorkingDirectoryConfiguration.WorkingDirectory(tempDir, false),
            NodeIdProvider.staticProvider(0),
            properties,
            new LifecycleProperties(),
            meterRegistry);
    final var brokerMeterRegistry = configuration.brokerMeterRegistry().registry();

    try {
      // when
      Counter.builder("broker.test").register(brokerMeterRegistry);

      // then
      assertThat(meterRegistry.get("broker.test").counter().getId().getTag("broker-id"))
          .isEqualTo(expectedNodeId);
    } finally {
      MicrometerUtil.close(brokerMeterRegistry);
      meterRegistry.close();
    }
  }

  private static Stream<Arguments> nodeIdConfigurations() {
    return Stream.of(Arguments.of("unzoned", false, "0"), Arguments.of("zoned", true, "zone-a_0"));
  }
}

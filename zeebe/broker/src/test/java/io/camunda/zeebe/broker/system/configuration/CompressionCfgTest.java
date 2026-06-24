/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.messaging.MessagingConfig.CompressionAlgorithm;
import java.util.Map;
import org.junit.Test;

public final class CompressionCfgTest {

  @Test
  public void shouldConfigureCompressionAlgorithm() {
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("compression-cfg", Map.of());
    final ClusterCfg config = cfg.getCluster();

    // then
    assertThat(config.getMessageCompression()).isEqualTo(CompressionAlgorithm.SNAPPY);
  }

  @Test
  public void shouldSetDefaultCompression() {
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("cluster-cfg", Map.of());
    final ClusterCfg config = cfg.getCluster();

    // then
    assertThat(config.getMessageCompression()).isEqualTo(CompressionAlgorithm.NONE);
  }
}

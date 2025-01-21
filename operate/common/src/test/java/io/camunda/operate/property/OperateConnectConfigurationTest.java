/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.property;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.connect.configuration.ConnectConfiguration;
import org.junit.jupiter.api.Test;

public class OperateConnectConfigurationTest {

  @Test
  public void shouldUseValuesConfiguredInLegacyConfigIfNotOverridden() {
    final var legacyES = new OperateElasticsearchProperties();
    final var connectConfiguration = new ConnectConfiguration();

    // given
    // some overrides in the new config
    connectConfiguration.setUrl("http://my-cluster:9300");
    connectConfiguration.setUsername("admin");
    connectConfiguration.setPassword("admin23");
    connectConfiguration.setIndexPrefix("camunda");

    // some legacy values
    legacyES.setIndexPrefix("");
    legacyES.setNumberOfReplicas(3);
    legacyES.setNumberOfShards(2);
    legacyES.setClusterName("clusterName1");

    // when
    final var merged = new OperateConnectConfiguration(connectConfiguration, legacyES, null);

    // then
    assertThat(merged.getUrl()).isEqualTo("http://my-cluster:9300");
    assertThat(merged.getUsername()).isEqualTo("admin");
    assertThat(merged.getPassword()).isEqualTo("admin23");
    assertThat(merged.getIndexPrefix()).isEqualTo("camunda");
    assertThat(merged.getNumberOfShards()).isEqualTo(2);
    assertThat(merged.getNumberOfReplicas()).isEqualTo(3);
    assertThat(merged.getClusterName()).isEqualTo("clusterName1");
  }
}

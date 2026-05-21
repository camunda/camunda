/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.container.ExtendedConfigurationBuilder;
import java.util.Map;
import org.junit.jupiter.api.Test;

@SuppressWarnings("resource")
final class RefreshablePropertiesTest {

  @Test
  void shouldRemoveStaleUnifiedConfigKeysOnRestart() {
    // given a broker with the recording exporter enabled
    final var broker = new TestStandaloneBroker().withRecordingExporter(true);

    // simulate the first start: createSpringBuilder() is called as part of start(); since we don't
    // want to actually boot Spring here, we instead invoke the same hook directly by re-emitting
    // the flat properties via withRefreshableProperties, mirroring what createSpringBuilder() does.
    broker.withRefreshableProperties(
        ExtendedConfigurationBuilder.flatPropertiesFor(broker.unifiedConfig()));

    final var classNameKey =
        "camunda.data.exporters." + TestStandaloneBroker.RECORDING_EXPORTER_ID + ".class-name";
    assertThat(
            broker.property(
                classNameKey,
                String.class,
                /* fallback to a sentinel to distinguish absent from null */
                "<absent>"))
        .as("recording exporter class-name should be exposed on first start")
        .isNotEqualTo("<absent>");

    // when the exporter is removed and the broker is "restarted"
    broker.withRecordingExporter(false);
    broker.withRefreshableProperties(
        ExtendedConfigurationBuilder.flatPropertiesFor(broker.unifiedConfig()));

    // then the previously-emitted exporter property is gone
    assertThat(broker.property(classNameKey, String.class, "<absent>"))
        .as("recording exporter class-name should be cleared on restart with the exporter removed")
        .isEqualTo("<absent>");
  }

  @Test
  void shouldNotClearUserSetPropertiesOnRefresh() {
    // given a broker with a user-set property that is not part of unified config
    final var broker = new TestStandaloneBroker();
    broker.withProperty("camunda.security.authorizations.enabled", true);

    // when refreshable properties are applied and then re-applied
    broker.withRefreshableProperties(
        ExtendedConfigurationBuilder.flatPropertiesFor(broker.unifiedConfig()));
    broker.withRefreshableProperties(
        ExtendedConfigurationBuilder.flatPropertiesFor(broker.unifiedConfig()));

    // then the user-set property survives both refreshes
    assertThat(broker.property("camunda.security.authorizations.enabled", Boolean.class, false))
        .as("user-set property should not be tracked as refreshable")
        .isTrue();
  }

  @Test
  void shouldOverrideStaleKeysWithNewValuesOnRefresh() {
    // given a broker with an initial cluster size
    final var broker = new TestStandaloneBroker();
    broker.withClusterConfig(c -> c.setSize(2));
    broker.withRefreshableProperties(
        ExtendedConfigurationBuilder.flatPropertiesFor(broker.unifiedConfig()));
    assertThat(broker.property("camunda.cluster.size", Integer.class, 0)).isEqualTo(2);

    // when the cluster size is changed and refresh runs again
    broker.withClusterConfig(c -> c.setSize(5));
    broker.withRefreshableProperties(
        ExtendedConfigurationBuilder.flatPropertiesFor(broker.unifiedConfig()));

    // then the new value is reflected (and the old one is replaced, not left behind)
    assertThat(broker.property("camunda.cluster.size", Integer.class, 0)).isEqualTo(5);
  }

  @Test
  void shouldEmitEmptyMapAfterClearingAllUnifiedConfigChanges() {
    // given a broker with various unified-config changes and a refresh applied
    final var broker = new TestStandaloneBroker();
    broker.withClusterConfig(c -> c.setSize(3));
    broker.withDataConfig(d -> d.setExporters(Map.of()));
    broker.withRefreshableProperties(
        ExtendedConfigurationBuilder.flatPropertiesFor(broker.unifiedConfig()));
    assertThat(broker.property("camunda.cluster.size", Integer.class, 0)).isEqualTo(3);

    // when all the changes are reset to defaults and refresh runs again
    broker.withClusterConfig(c -> c.setSize(1));
    broker.withRefreshableProperties(
        ExtendedConfigurationBuilder.flatPropertiesFor(broker.unifiedConfig()));

    // then the previously-set size is no longer present
    assertThat(broker.property("camunda.cluster.size", Integer.class, /* sentinel */ -1))
        .as("size now matches pristine default and should not be emitted")
        .isEqualTo(-1);
  }
}

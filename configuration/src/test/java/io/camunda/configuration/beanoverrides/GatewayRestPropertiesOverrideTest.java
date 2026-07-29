/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beanoverrides;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beans.GatewayRestProperties;
import io.camunda.configuration.beans.LegacyGatewayRestProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;

class GatewayRestPropertiesOverrideTest {

  @BeforeAll
  @AfterAll
  static void clearStaticEnvironment() {
    // The Camunda config getters delegate to UnifiedConfigurationHelper, which short-circuits
    // when its static environment is null. Clear it so a previous Spring-based test in the
    // same JVM doesn't leak its environment into these plain unit tests.
    UnifiedConfigurationHelper.setCustomEnvironment(null);
  }

  /**
   * Reproduces the copy performed by {@code gatewayRestProperties()}: consumers are injected with
   * the {@code @Primary} {@link GatewayRestProperties} bean, so anything that fails to survive this
   * copy is invisible at runtime even when configured.
   */
  private static GatewayRestProperties copyOf(final LegacyGatewayRestProperties legacy) {
    final GatewayRestProperties override = new GatewayRestProperties();
    BeanUtils.copyProperties(legacy, override);
    GatewayRestPropertiesOverride.copyReadOnlyNestedProperties(legacy, override);
    return override;
  }

  @Test
  void shouldPropagateEnabledUpdateMetadataFlag() {
    // given - as bound from camunda.rest.update-metadata.enabled=true
    final LegacyGatewayRestProperties legacy = new LegacyGatewayRestProperties();
    legacy.getUpdateMetadata().setEnabled(true);

    // when
    final GatewayRestProperties override = copyOf(legacy);

    // then - BeanUtils.copyProperties cannot write this read-only nested property on its own
    assertThat(override.getUpdateMetadata().isEnabled()).isTrue();
  }

  @Test
  void shouldKeepUpdateMetadataDisabledByDefault() {
    // given
    final LegacyGatewayRestProperties legacy = new LegacyGatewayRestProperties();

    // when
    final GatewayRestProperties override = copyOf(legacy);

    // then
    assertThat(override.getUpdateMetadata().isEnabled()).isFalse();
  }

  @Test
  void shouldStillCopyWritableScalarProperties() {
    // given
    final LegacyGatewayRestProperties legacy = new LegacyGatewayRestProperties();
    legacy.setMaxNameFieldLength(4242);

    // when
    final GatewayRestProperties override = copyOf(legacy);

    // then
    assertThat(override.getMaxNameFieldLength()).isEqualTo(4242);
  }
}

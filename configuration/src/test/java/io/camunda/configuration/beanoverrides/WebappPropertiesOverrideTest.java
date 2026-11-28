/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beanoverrides;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.beans.WebappProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

class WebappPropertiesOverrideTest {

  @Test
  void shouldAdvertiseAllComponentsByDefault() {
    // given
    final var environment = new MockEnvironment();

    // when
    final var properties = resolve(environment);

    // then
    assertThat(properties.getActiveComponents()).containsExactly("admin", "operate", "tasklist");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "camunda.webapps.admin.ui-enabled",
        "camunda.webapps.identity.ui-enabled",
        "camunda.webapps.admin.enabled",
        "camunda.webapps.identity.enabled"
      })
  void shouldNotAdvertiseAdminWhenEitherIdentityOrAdminSwitchIsOff(final String property) {
    // given
    final var environment = new MockEnvironment().withProperty(property, "false");

    // when
    final var properties = resolve(environment);

    // then
    assertThat(properties.getActiveComponents()).containsExactly("operate", "tasklist");
  }

  private static WebappProperties resolve(final MockEnvironment environment) {
    return new WebappPropertiesOverride(
            new WebappProperties(), new LegacyOperateProperties(), environment)
        .webappProperties();
  }
}

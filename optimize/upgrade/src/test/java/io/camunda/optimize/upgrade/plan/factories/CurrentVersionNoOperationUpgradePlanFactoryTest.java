/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.plan.factories;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

import io.camunda.optimize.service.metadata.Version;
import io.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import io.camunda.optimize.upgrade.util.VersionUtils;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CurrentVersionNoOperationUpgradePlanFactoryTest {

  @Test
  void createUpgradePlanThrowsWhenPreviousMinorVersionUncomputable() {
    try (final var versionUtils = mockStatic(VersionUtils.class)) {
      // given
      versionUtils
          .when(() -> VersionUtils.previousMinorVersion(Version.VERSION))
          .thenReturn(Optional.empty());

      // when - then
      assertThatThrownBy(
              () -> new CurrentVersionNoOperationUpgradePlanFactory().createUpgradePlan())
          .isInstanceOf(UpgradeRuntimeException.class)
          .hasMessageContaining(
              "Cannot compute previous minor version from %s. An explicit UpgradePlanFactory is required.",
              Version.VERSION);
    }
  }
}

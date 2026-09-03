/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.UnifiedConfigurationException;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ArchiverlessSecondaryStorageValidationTest {
  private final ArchiverlessSecondaryStorageValidation validation =
      new ArchiverlessSecondaryStorageValidation();

  @ParameterizedTest
  @EnumSource(
      value = SecondaryStorageType.class,
      names = {"elasticsearch", "opensearch"})
  void shouldAllowArchiverless(final SecondaryStorageType secondaryStorageType) {
    validation.validate(Map.of("tenant1", camunda(true, secondaryStorageType)));
  }

  @ParameterizedTest
  @EnumSource(
      value = SecondaryStorageType.class,
      names = {"elasticsearch", "opensearch"},
      mode = EnumSource.Mode.EXCLUDE)
  void shouldBlockArchiverless(final SecondaryStorageType secondaryStorageType) {
    assertThatThrownBy(
            () -> validation.validate(Map.of("tenant1", camunda(true, secondaryStorageType))))
        .isInstanceOf(UnifiedConfigurationException.class)
        .hasMessageContaining(
            String.format(
                "Tenant 'tenant1' is configured with secondary storage %s, which is not supported when archiver-less is enabled.",
                secondaryStorageType));
  }

  @ParameterizedTest
  @EnumSource(SecondaryStorageType.class)
  void shouldAllowAllWhenNotArchiverless(final SecondaryStorageType secondaryStorageType) {
    validation.validate(Map.of("tenant1", camunda(false, secondaryStorageType)));
  }

  private static Camunda camunda(
      final boolean archiverless, final SecondaryStorageType secondaryStorageType) {
    final Camunda camunda = new Camunda();
    camunda.getProcessing().getEngine().getStorageOrdinals().setEnableArchiverless(archiverless);
    camunda.getData().getSecondaryStorage().setType(secondaryStorageType);
    return camunda;
  }
}

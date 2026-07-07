/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.serving.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.analytics.dataset.DatasetCompiler;
import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.meter.InMemoryMeterIdStore;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.meter.MeterIdRegistry;
import org.junit.jupiter.api.Test;

/**
 * Guards the standard declarations against the declaration-time validation (tier rules, name
 * charset, meter params): every shipped dataset must always pass the same gate a user-declared
 * dataset does.
 */
final class StandardDatasetsTest {

  @Test
  void shouldCompileEveryStandardDeclarationThroughTheValidationGate() {
    // given the validation-gating compiler a runtime declaration goes through
    final DatasetCompiler compiler =
        new DatasetCompiler(
            MeterCatalog.withDefaults(), new MeterIdRegistry(new InMemoryMeterIdStore()));

    // when / then every standard cube declaration constructs and compiles
    long cubeId = 1;
    assertThat(StandardDatasets.declarations()).isNotEmpty();
    for (final DatasetDeclaration declaration : StandardDatasets.declarations()) {
      final long id = cubeId++;
      assertThatCode(() -> compiler.compile(id, declaration))
          .as("standard dataset '%s'", declaration.name())
          .doesNotThrowAnyException();
    }
    // and every standard table declaration as well
    assertThat(StandardDatasets.tableDeclarations()).isNotEmpty();
    for (final DatasetDeclaration declaration : StandardDatasets.tableDeclarations()) {
      final long id = cubeId++;
      assertThatCode(() -> compiler.compileTable(id, declaration))
          .as("standard table '%s'", declaration.name())
          .doesNotThrowAnyException();
    }
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beanoverrides;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.SecondaryStorage;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beans.SearchEngineSchemaManagerProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SearchEngineSchemaManagerPropertiesOverrideTest {

  @BeforeAll
  @AfterAll
  static void clearStaticEnvironment() {
    // The Camunda config getters delegate to UnifiedConfigurationHelper, which short-circuits
    // when its static environment is null. Clear it so a previous Spring-based test in the
    // same JVM doesn't leak its environment into these plain unit tests.
    UnifiedConfigurationHelper.setCustomEnvironment(null);
  }

  @Test
  void shouldApplyVersionCheckRestrictionEnabled() {
    // given
    final Camunda camunda = new Camunda();
    camunda.getSystem().getUpgrade().setEnableVersionCheck(false);

    final SearchEngineSchemaManagerProperties override = new SearchEngineSchemaManagerProperties();

    // when
    SearchEngineSchemaManagerPropertiesOverride.applyTo(camunda, override);

    // then
    assertThat(override.isVersionCheckRestrictionEnabled()).isFalse();
  }

  @Test
  void shouldApplyPerformCleanupAndCreateSchemaForElasticsearch() {
    // given
    final Camunda camunda = new Camunda();
    final SecondaryStorage secondaryStorage = camunda.getData().getSecondaryStorage();
    secondaryStorage.setType(SecondaryStorageType.elasticsearch);
    secondaryStorage.getElasticsearch().setPerformCleanup(true);
    secondaryStorage.getElasticsearch().setCreateSchema(false);

    final SearchEngineSchemaManagerProperties override = new SearchEngineSchemaManagerProperties();

    // when
    SearchEngineSchemaManagerPropertiesOverride.applyTo(camunda, override);

    // then
    assertThat(override.isPerformCleanup()).isTrue();
    assertThat(override.isCreateSchema()).isFalse();
  }

  @Test
  void shouldApplyPerformCleanupAndCreateSchemaForOpensearch() {
    // given
    final Camunda camunda = new Camunda();
    final SecondaryStorage secondaryStorage = camunda.getData().getSecondaryStorage();
    secondaryStorage.setType(SecondaryStorageType.opensearch);
    secondaryStorage.getOpensearch().setPerformCleanup(true);
    secondaryStorage.getOpensearch().setCreateSchema(false);

    final SearchEngineSchemaManagerProperties override = new SearchEngineSchemaManagerProperties();

    // when
    SearchEngineSchemaManagerPropertiesOverride.applyTo(camunda, override);

    // then
    assertThat(override.isPerformCleanup()).isTrue();
    assertThat(override.isCreateSchema()).isFalse();
  }

  @Test
  void shouldNotTouchPerformCleanupOrCreateSchemaForRdbms() {
    // given
    final Camunda camunda = new Camunda();
    final SecondaryStorage secondaryStorage = camunda.getData().getSecondaryStorage();
    secondaryStorage.setType(SecondaryStorageType.rdbms);

    // and an override pre-populated as if seeded from the legacy properties
    final SearchEngineSchemaManagerProperties override = new SearchEngineSchemaManagerProperties();
    override.setPerformCleanup(true);
    override.setCreateSchema(false);

    // when
    SearchEngineSchemaManagerPropertiesOverride.applyTo(camunda, override);

    // then RDBMS is not a document-based database, so these fields are left untouched
    assertThat(override.isPerformCleanup()).isTrue();
    assertThat(override.isCreateSchema()).isFalse();
  }

  @Test
  void shouldAlwaysApplyVersionCheckRegardlessOfSecondaryStorageType() {
    // given
    final Camunda camunda = new Camunda();
    camunda.getSystem().getUpgrade().setEnableVersionCheck(false);
    camunda.getData().getSecondaryStorage().setType(SecondaryStorageType.rdbms);

    final SearchEngineSchemaManagerProperties override = new SearchEngineSchemaManagerProperties();

    // when
    SearchEngineSchemaManagerPropertiesOverride.applyTo(camunda, override);

    // then
    assertThat(override.isVersionCheckRestrictionEnabled()).isFalse();
  }
}

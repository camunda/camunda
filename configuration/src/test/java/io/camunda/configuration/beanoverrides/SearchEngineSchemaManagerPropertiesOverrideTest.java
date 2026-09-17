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
import java.time.Duration;
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
  void shouldApplyPerformCleanupCreateSchemaAndHealthCheckForElasticsearch() {
    // given
    final Camunda camunda = new Camunda();
    final SecondaryStorage secondaryStorage = camunda.getData().getSecondaryStorage();
    secondaryStorage.setType(SecondaryStorageType.elasticsearch);
    secondaryStorage.getElasticsearch().setPerformCleanup(true);
    secondaryStorage.getElasticsearch().setCreateSchema(false);
    secondaryStorage.getElasticsearch().setHealthCheckEnabled(false);

    final SearchEngineSchemaManagerProperties override = new SearchEngineSchemaManagerProperties();

    // when
    SearchEngineSchemaManagerPropertiesOverride.applyTo(camunda, override);

    // then
    assertThat(override.isPerformCleanup()).isTrue();
    assertThat(override.isCreateSchema()).isFalse();
    assertThat(override.isHealthCheckEnabled()).isFalse();
  }

  @Test
  void shouldApplyPerformCleanupCreateSchemaAndHealthCheckForOpensearch() {
    // given
    final Camunda camunda = new Camunda();
    final SecondaryStorage secondaryStorage = camunda.getData().getSecondaryStorage();
    secondaryStorage.setType(SecondaryStorageType.opensearch);
    secondaryStorage.getOpensearch().setPerformCleanup(true);
    secondaryStorage.getOpensearch().setCreateSchema(false);
    secondaryStorage.getOpensearch().setHealthCheckEnabled(false);

    final SearchEngineSchemaManagerProperties override = new SearchEngineSchemaManagerProperties();

    // when
    SearchEngineSchemaManagerPropertiesOverride.applyTo(camunda, override);

    // then
    assertThat(override.isPerformCleanup()).isTrue();
    assertThat(override.isCreateSchema()).isFalse();
    assertThat(override.isHealthCheckEnabled()).isFalse();
  }

  @Test
  void shouldNotTouchDocumentBasedSchemaSettingsForRdbms() {
    // given
    final Camunda camunda = new Camunda();
    final SecondaryStorage secondaryStorage = camunda.getData().getSecondaryStorage();
    secondaryStorage.setType(SecondaryStorageType.rdbms);

    // and an override pre-populated as if seeded from the legacy properties
    final SearchEngineSchemaManagerProperties override = new SearchEngineSchemaManagerProperties();
    override.setPerformCleanup(true);
    override.setCreateSchema(false);
    override.setHealthCheckEnabled(false);

    // when
    SearchEngineSchemaManagerPropertiesOverride.applyTo(camunda, override);

    // then RDBMS is not a document-based database, so these fields are left untouched
    assertThat(override.isPerformCleanup()).isTrue();
    assertThat(override.isCreateSchema()).isFalse();
    assertThat(override.isHealthCheckEnabled()).isFalse();
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

  @Test
  void shouldApplyRetryForElasticsearch() {
    // given
    final Camunda camunda = new Camunda();
    final SecondaryStorage secondaryStorage = camunda.getData().getSecondaryStorage();
    secondaryStorage.setType(SecondaryStorageType.elasticsearch);
    secondaryStorage.getElasticsearch().getRetry().setMaxRetries(7);
    secondaryStorage.getElasticsearch().getRetry().setMinRetryDelay(Duration.ofSeconds(1));
    secondaryStorage.getElasticsearch().getRetry().setMaxRetryDelay(Duration.ofSeconds(20));

    final SearchEngineSchemaManagerProperties override = new SearchEngineSchemaManagerProperties();

    // when
    SearchEngineSchemaManagerPropertiesOverride.applyTo(camunda, override);

    // then
    assertThat(override.getRetry().getMaxRetries()).isEqualTo(7);
    assertThat(override.getRetry().getMinRetryDelay()).isEqualTo(Duration.ofSeconds(1));
    assertThat(override.getRetry().getMaxRetryDelay()).isEqualTo(Duration.ofSeconds(20));
  }

  @Test
  void shouldApplyRetryForOpensearch() {
    // given
    final Camunda camunda = new Camunda();
    final SecondaryStorage secondaryStorage = camunda.getData().getSecondaryStorage();
    secondaryStorage.setType(SecondaryStorageType.opensearch);
    secondaryStorage.getOpensearch().getRetry().setMaxRetries(7);

    final SearchEngineSchemaManagerProperties override = new SearchEngineSchemaManagerProperties();

    // when
    SearchEngineSchemaManagerPropertiesOverride.applyTo(camunda, override);

    // then
    assertThat(override.getRetry().getMaxRetries()).isEqualTo(7);
  }

  @Test
  void shouldApplyUnboundedRetryByDefault() {
    // given
    final Camunda camunda = new Camunda();
    final SecondaryStorage secondaryStorage = camunda.getData().getSecondaryStorage();
    secondaryStorage.setType(SecondaryStorageType.elasticsearch);

    final SearchEngineSchemaManagerProperties override = new SearchEngineSchemaManagerProperties();

    // when
    SearchEngineSchemaManagerPropertiesOverride.applyTo(camunda, override);

    // then
    assertThat(override.getRetry().getMaxRetries()).isEqualTo(Integer.MAX_VALUE);
    assertThat(override.getRetry().getMinRetryDelay()).isEqualTo(Duration.ofMillis(500));
    assertThat(override.getRetry().getMaxRetryDelay()).isEqualTo(Duration.ofSeconds(10));
  }

  /**
   * The override shares its retry instance with the legacy bean after {@code
   * BeanUtils.copyProperties}, so applying must replace it rather than mutate it in place.
   */
  @Test
  void shouldReplaceRetryInstanceRatherThanMutateIt() {
    // given
    final Camunda camunda = new Camunda();
    final SecondaryStorage secondaryStorage = camunda.getData().getSecondaryStorage();
    secondaryStorage.setType(SecondaryStorageType.elasticsearch);
    secondaryStorage.getElasticsearch().getRetry().setMaxRetries(7);

    final SearchEngineSchemaManagerProperties override = new SearchEngineSchemaManagerProperties();
    final var sharedRetry = override.getRetry();
    sharedRetry.setMaxRetries(3);

    // when
    SearchEngineSchemaManagerPropertiesOverride.applyTo(camunda, override);

    // then
    assertThat(override.getRetry()).isNotSameAs(sharedRetry);
    assertThat(sharedRetry.getMaxRetries()).isEqualTo(3);
  }

  @Test
  void shouldNotTouchRetryForRdbms() {
    // given
    final Camunda camunda = new Camunda();
    camunda.getData().getSecondaryStorage().setType(SecondaryStorageType.rdbms);

    final SearchEngineSchemaManagerProperties override = new SearchEngineSchemaManagerProperties();
    final var untouchedRetry = override.getRetry();

    // when
    SearchEngineSchemaManagerPropertiesOverride.applyTo(camunda, override);

    // then
    assertThat(override.getRetry()).isSameAs(untouchedRetry);
  }
}

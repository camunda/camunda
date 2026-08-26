/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.query.job.EntityType;
import io.camunda.optimize.dto.optimize.query.job.JobType;
import io.camunda.optimize.service.db.reader.JobRegistryReader;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.DeletedProcessDefinitionCacheConfiguration;
import io.camunda.optimize.service.util.configuration.GlobalCacheConfiguration;
import io.camunda.optimize.service.util.configuration.ZeebeConfiguration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeletedProcessDefinitionCacheTest {

  private static final int TEST_MAX_SIZE = 3;

  private JobRegistryReader jobRegistryReader;
  private ZeebeConfiguration zeebeConfiguration;
  private DeletedProcessDefinitionCache cache;

  @BeforeEach
  void setUp() {
    jobRegistryReader = mock(JobRegistryReader.class);
    final DeletedProcessDefinitionCacheConfiguration cacheConfig =
        new DeletedProcessDefinitionCacheConfiguration();
    cacheConfig.setMaxSize(TEST_MAX_SIZE);
    cacheConfig.setRefreshIntervalSeconds(5);
    final GlobalCacheConfiguration globalCacheConfiguration = mock(GlobalCacheConfiguration.class);
    when(globalCacheConfiguration.getDeletedProcessDefinitions()).thenReturn(cacheConfig);
    zeebeConfiguration = mock(ZeebeConfiguration.class);
    when(zeebeConfiguration.isEnabled()).thenReturn(true);
    final ConfigurationService configurationService = mock(ConfigurationService.class);
    when(configurationService.getCaches()).thenReturn(globalCacheConfiguration);
    when(configurationService.getConfiguredZeebe()).thenReturn(zeebeConfiguration);
    cache = new DeletedProcessDefinitionCache(jobRegistryReader, configurationService);
  }

  @AfterEach
  void tearDown() {
    cache.destroy();
  }

  @Test
  void shouldStartSchedulingOnInitWhenZeebeImportEnabled() {
    // when
    cache.init();

    // then
    assertThat(cache.isScheduledToRun()).isTrue();
  }

  @Test
  void shouldNotStartSchedulingOnInitWhenZeebeImportDisabled() {
    // given a cache built for a configuration where the zeebe importer is disabled, e.g. a
    // webapp-only Optimize deployment that never writes process definitions
    when(zeebeConfiguration.isEnabled()).thenReturn(false);
    final DeletedProcessDefinitionCacheConfiguration cacheConfig =
        new DeletedProcessDefinitionCacheConfiguration();
    cacheConfig.setMaxSize(TEST_MAX_SIZE);
    cacheConfig.setRefreshIntervalSeconds(5);
    final GlobalCacheConfiguration globalCacheConfiguration = mock(GlobalCacheConfiguration.class);
    when(globalCacheConfiguration.getDeletedProcessDefinitions()).thenReturn(cacheConfig);
    final ConfigurationService configurationService = mock(ConfigurationService.class);
    when(configurationService.getCaches()).thenReturn(globalCacheConfiguration);
    when(configurationService.getConfiguredZeebe()).thenReturn(zeebeConfiguration);
    final DeletedProcessDefinitionCache disabledCache =
        new DeletedProcessDefinitionCache(jobRegistryReader, configurationService);

    // when
    disabledCache.init();

    // then
    assertThat(disabledCache.isScheduledToRun()).isFalse();
  }

  @Test
  void shouldTriggerLazyInitialLoadOnFirstIsSuppressedCall() {
    // given
    when(jobRegistryReader.findNewestEntityIds(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, TEST_MAX_SIZE))
        .thenReturn(List.of());

    // when isSuppressed is called before any explicit run()
    final boolean result = cache.isSuppressed("1");

    // then the cache performed a synchronous load rather than starting empty and waiting for
    // the periodic scheduler's first tick
    assertThat(result).isFalse();
    verify(jobRegistryReader)
        .findNewestEntityIds(JobType.DELETE, EntityType.PROCESS_DEFINITION, TEST_MAX_SIZE);
  }

  @Test
  void shouldContainEntriesFromLatestRefresh() {
    // given
    when(jobRegistryReader.findNewestEntityIds(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, TEST_MAX_SIZE))
        .thenReturn(List.of("1", "2"));

    // when
    cache.run();

    // then
    assertThat(cache.isSuppressed("1")).isTrue();
    assertThat(cache.isSuppressed("2")).isTrue();
    assertThat(cache.isSuppressed("3")).isFalse();
  }

  @Test
  void shouldReplaceCacheContentsOnEachRefreshRatherThanMerge() {
    // given
    when(jobRegistryReader.findNewestEntityIds(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, TEST_MAX_SIZE))
        .thenReturn(List.of("1"));
    cache.run();
    assertThat(cache.isSuppressed("1")).isTrue();

    // when the next refresh no longer returns "1" (e.g. it aged out beyond maxSize)
    when(jobRegistryReader.findNewestEntityIds(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, TEST_MAX_SIZE))
        .thenReturn(List.of("2"));
    cache.run();

    // then the cache reflects only the latest fetch, not the union of both
    assertThat(cache.isSuppressed("1")).isFalse();
    assertThat(cache.isSuppressed("2")).isTrue();
  }

  @Test
  void shouldReplaceWithEmptyResultWhenFetchReturnsNothing() {
    // given
    when(jobRegistryReader.findNewestEntityIds(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, TEST_MAX_SIZE))
        .thenReturn(List.of("1"));
    cache.run();
    assertThat(cache.isSuppressed("1")).isTrue();

    // when a later refresh legitimately finds nothing (e.g. registry temporarily empty in tests)
    when(jobRegistryReader.findNewestEntityIds(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, TEST_MAX_SIZE))
        .thenReturn(List.of());
    cache.run();

    // then the cache is cleared, since each refresh replaces rather than merges
    assertThat(cache.isSuppressed("1")).isFalse();
  }

  @Test
  void shouldSwallowReaderErrorsAndKeepPreviousState() {
    // given
    when(jobRegistryReader.findNewestEntityIds(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, TEST_MAX_SIZE))
        .thenReturn(List.of("1"));
    cache.run();
    when(jobRegistryReader.findNewestEntityIds(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, TEST_MAX_SIZE))
        .thenThrow(new RuntimeException("boom"));

    // when
    cache.run();

    // then a failed refresh never reaches the replace step, so the last successful state stands
    assertThat(cache.isSuppressed("1")).isTrue();
  }
}

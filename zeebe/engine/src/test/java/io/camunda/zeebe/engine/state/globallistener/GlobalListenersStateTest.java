/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.globallistener;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateRule;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerRecord;
import java.util.List;
import java.util.Random;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class GlobalListenersStateTest {

  @Rule public final ProcessingStateRule stateRule = new ProcessingStateRule();

  private MutableGlobalListenersState globalListenersState;

  private long key = 1L;

  @Before
  public void setUp() {
    final MutableProcessingState processingState = stateRule.getProcessingState();
    globalListenersState = processingState.getGlobalListenersState();
  }

  @Test
  public void shouldCreateFirstConfiguration() {
    // given a global listeners record
    final GlobalListenerBatchRecord expectedConfig = newGlobalListeners();

    // when the configuration is stored in state
    updateCurrentConfiguration(expectedConfig);

    // then the configuration can be retrieved from state
    final var storedConfig = globalListenersState.getCurrentConfig();
    assertThat(storedConfig).isNotNull().isEqualTo(expectedConfig);
  }

  @Test
  public void shouldUpdateConfiguration() {
    // given a global listeners configuration stored in state
    final GlobalListenerBatchRecord firstConfig = newGlobalListeners();
    updateCurrentConfiguration(firstConfig);

    // when the configuration is updated
    final GlobalListenerBatchRecord newConfig = newGlobalListeners();
    updateCurrentConfiguration(newConfig);

    // then the new configuration can be retrieved from state
    final var storedConfig = globalListenersState.getCurrentConfig();
    assertThat(storedConfig).isNotNull().isEqualTo(newConfig);
  }

  @Test
  public void shouldStoreConfigurationVersion() {
    // when a configuration is stored as a versioned configuration
    final GlobalListenerBatchRecord expectedConfig = newGlobalListeners();
    final long versionKey = globalListenersState.storeConfigurationVersion(expectedConfig);

    // then the versioned configuration can be retrieved from state
    Assertions.assertThat(globalListenersState.isConfigurationVersionStored(versionKey)).isTrue();
    final var versioned = globalListenersState.getVersionedConfig(versionKey);
    assertThat(versioned).isNotNull().isEqualTo(expectedConfig);
  }

  @Test
  public void shouldNotChangeStoredConfigurationVersionIfGlobalConfigurationChanges() {
    // given a configuration stored as a versioned configuration
    final GlobalListenerBatchRecord expectedConfig = newGlobalListeners();
    final long versionKey = globalListenersState.storeConfigurationVersion(expectedConfig);

    // whe the current global listeners configuration is updated
    final GlobalListenerBatchRecord newConfig = newGlobalListeners();
    updateCurrentConfiguration(newConfig);

    // then the versioned configuration can still be retrieved from state
    Assertions.assertThat(globalListenersState.isConfigurationVersionStored(versionKey)).isTrue();
    final var versioned = globalListenersState.getVersionedConfig(versionKey);
    assertThat(versioned).isNotNull().isEqualTo(expectedConfig);
  }

  @Test
  public void shouldDeleteConfigurationVersion() {
    // given a configuration stored as a versioned configuration
    final GlobalListenerBatchRecord expectedConfig = newGlobalListeners();
    final long versionKey = globalListenersState.storeConfigurationVersion(expectedConfig);

    // whe the versioned copy is explicitly deleted
    globalListenersState.deleteConfigurationVersion(versionKey);

    // then the versioned configuration is no longer available
    Assertions.assertThat(globalListenersState.isConfigurationVersionStored(versionKey)).isFalse();
    final var versioned = globalListenersState.getVersionedConfig(versionKey);
    Assertions.assertThat(versioned).isNull();
  }

  @Test
  public void shouldPinConfigurationVersion() {
    // given a configuration stored as a versioned configuration
    final GlobalListenerBatchRecord expectedConfig = newGlobalListeners();
    final long versionKey = globalListenersState.storeConfigurationVersion(expectedConfig);

    // whe the version is pinned by an element
    final long elementKey = newKey();
    globalListenersState.pinConfiguration(versionKey, elementKey);

    // then the versioned configuration should be marked as pinned
    Assertions.assertThat(globalListenersState.isConfigurationVersionPinned(versionKey)).isTrue();
  }

  @Test
  public void
      shouldKeepConfigurationVersionPinnedIfItIsUnpinnedFromOneElementButReferencedByAnother() {
    // given a configuration stored as a versioned configuration and pinned by two elements
    final GlobalListenerBatchRecord expectedConfig = newGlobalListeners();
    final long versionKey = globalListenersState.storeConfigurationVersion(expectedConfig);
    final long elementKey1 = newKey();
    final long elementKey2 = newKey();
    globalListenersState.pinConfiguration(versionKey, elementKey1);
    globalListenersState.pinConfiguration(versionKey, elementKey2);

    // whe the version is unpinned by one of the element
    globalListenersState.unpinConfiguration(versionKey, elementKey1);

    // then the versioned configuration should still be marked as pinned
    Assertions.assertThat(globalListenersState.isConfigurationVersionPinned(versionKey)).isTrue();
  }

  @Test
  public void shouldUnpinConfigurationVersionIfItIsUnpinnedFromAllReferencingElements() {
    // given a configuration stored as a versioned configuration and pinned by two elements
    final GlobalListenerBatchRecord expectedConfig = newGlobalListeners();
    final long versionKey = globalListenersState.storeConfigurationVersion(expectedConfig);
    final long elementKey1 = newKey();
    final long elementKey2 = newKey();
    globalListenersState.pinConfiguration(versionKey, elementKey1);
    globalListenersState.pinConfiguration(versionKey, elementKey2);

    // when both user tasks unpin the configuration
    globalListenersState.unpinConfiguration(versionKey, elementKey1);
    globalListenersState.unpinConfiguration(versionKey, elementKey2);

    // then
    Assertions.assertThat(globalListenersState.isConfigurationVersionPinned(versionKey)).isFalse();
  }

  private void updateCurrentConfiguration(final GlobalListenerBatchRecord newConfig) {
    final var oldConfig = globalListenersState.getCurrentConfig();
    if (oldConfig != null) {
      oldConfig
          .getTaskListeners()
          .forEach(listener -> globalListenersState.delete((GlobalListenerRecord) listener));
    }
    newConfig
        .getTaskListeners()
        .forEach(listener -> globalListenersState.create((GlobalListenerRecord) listener));
    globalListenersState.updateConfigKey(newConfig.getGlobalListenerBatchKey());
  }

  private GlobalListenerBatchRecord newGlobalListeners() {
    final GlobalListenerBatchRecord record =
        new GlobalListenerBatchRecord().setGlobalListenerBatchKey(newKey());
    final int numberOfListeners = new Random().nextInt(2, 10);
    for (int i = 0; i < numberOfListeners; i++) {
      record.addTaskListener(
          new GlobalListenerRecord()
              .setId("GlobalListener_" + i)
              .setType("global" + i)
              .setEventTypes(List.of("creating", "assigning"))
              .setRetries(i)
              .setAfterNonGlobal(i % 2 == 0));
    }
    return record;
  }

  private long newKey() {
    return key++;
  }
}

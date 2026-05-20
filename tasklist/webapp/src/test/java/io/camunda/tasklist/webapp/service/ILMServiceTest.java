/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.tasklist.management.ILMPolicyUpdate;
import io.camunda.tasklist.property.TasklistProperties;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ILMServiceTest {

  @Mock private ILMPolicyUpdate ilmPolicyUpdate;
  @Spy private TasklistProperties tasklistProperties;
  @InjectMocks private ILMService instance;

  @Test
  void shouldApplyPolicyWhenIlmEnabled() throws IOException {
    // given
    tasklistProperties.setArchiverEnabled(true);
    tasklistProperties.getArchiver().setIlmEnabled(true);
    tasklistProperties.getArchiver().setIlmManagePolicy(true);

    // when
    instance.init();

    // then
    verify(ilmPolicyUpdate, times(1)).applyIlmPolicyToAllIndices();
    verify(ilmPolicyUpdate, never()).removeIlmPolicyFromAllIndices();
  }

  @Test
  void shouldRemovePolicyWhenIlmDisabledAndManagePolicyEnabled() throws IOException {
    // given
    tasklistProperties.setArchiverEnabled(true);
    tasklistProperties.getArchiver().setIlmEnabled(false);
    tasklistProperties.getArchiver().setIlmManagePolicy(true);

    // when
    instance.init();

    // then
    verify(ilmPolicyUpdate, never()).applyIlmPolicyToAllIndices();
    verify(ilmPolicyUpdate, times(1)).removeIlmPolicyFromAllIndices();
  }

  @Test
  void shouldLeaveExistingPolicyAloneWhenIlmDisabledAndManagePolicyDisabled() throws IOException {
    // given
    tasklistProperties.setArchiverEnabled(true);
    tasklistProperties.getArchiver().setIlmEnabled(false);
    tasklistProperties.getArchiver().setIlmManagePolicy(false);

    // when
    instance.init();

    // then neither apply nor remove is invoked — pre-existing assignments are preserved
    verify(ilmPolicyUpdate, never()).applyIlmPolicyToAllIndices();
    verify(ilmPolicyUpdate, never()).removeIlmPolicyFromAllIndices();
  }

  @Test
  void shouldSkipEntirelyWhenArchiverDisabled() throws IOException {
    // given
    tasklistProperties.setArchiverEnabled(false);

    // when
    instance.init();

    // then
    verify(ilmPolicyUpdate, never()).applyIlmPolicyToAllIndices();
    verify(ilmPolicyUpdate, never()).removeIlmPolicyFromAllIndices();
  }
}

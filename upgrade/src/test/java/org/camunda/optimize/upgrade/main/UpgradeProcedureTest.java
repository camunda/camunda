/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.main;

import org.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import org.camunda.optimize.upgrade.plan.UpgradeExecutionPlan;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.service.UpgradeValidationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UpgradeProcedureTest {
  private static final String TARGET_VERSION = "2.6.0";
  private static final String FROM_VERSION = "2.5.0";

  @Mock
  private UpgradeExecutionPlan executionPlan;
  @Mock
  private UpgradeValidationService validationService;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private UpgradeExecutionDependencies upgradeDependencies;

  @Test
  public void validationIsDoneBeforeUpgradeExecution() {
    // given
    final UpgradeProcedure underTest = createUpgradeProcedure();
    when(upgradeDependencies.getMetadataService().getSchemaVersion(any())).thenReturn(Optional.of(FROM_VERSION));

    // when
    underTest.performUpgrade();

    // then the validation and execution happens in the expected order
    InOrder inOrder = inOrder(validationService, executionPlan);
    // The validation order matters since we first need to ensure that the ES client
    // is able to communicate to ElasticSearch before using it to retrieve the schema version.
    inOrder.verify(validationService).validateESVersion(any(), any());
    inOrder.verify(validationService).validateSchemaVersions(FROM_VERSION, FROM_VERSION, TARGET_VERSION);
    inOrder.verify(executionPlan).execute();
  }

  @Test
  public void executionSkippedOnSchemaVersionEqualToTargetVersion() {
    // given
    when(upgradeDependencies.getMetadataService().getSchemaVersion(any())).thenReturn(Optional.of(TARGET_VERSION));
    final UpgradeProcedure underTest = createUpgradeProcedure();

    // when
    underTest.performUpgrade();

    // then
    verify(validationService).validateSchemaVersions(TARGET_VERSION, FROM_VERSION, TARGET_VERSION);
    verify(executionPlan, never()).execute();
  }

  private UpgradeProcedure createUpgradeProcedure() {
    return new UpgradeProcedure(upgradeDependencies, validationService) {
      @Override
      public String getInitialVersion() {
        return FROM_VERSION;
      }

      @Override
      public String getTargetVersion() {
        return TARGET_VERSION;
      }

      @Override
      protected UpgradePlan buildUpgradePlan() {
        return executionPlan;
      }
    };
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.main;

import org.camunda.optimize.upgrade.plan.UpgradeExecutionPlan;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.service.UpgradeValidationService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

public class UpgradeProcedureTest {

  @Test
  public void validationIsDoneBeforeUpgradeExecution() {
    // given
    UpgradeExecutionPlan mockedExecutionPlan = mock(UpgradeExecutionPlan.class);
    UpgradeValidationService mockedValidationService = mock(UpgradeValidationService.class);
    final UpgradeProcedure underTest = mockUpgradeProcedure(mockedExecutionPlan);
    underTest.setUpgradeValidationService(mockedValidationService);

    // when
    underTest.performUpgrade();

    // then
    InOrder inOrder = inOrder(mockedValidationService, mockedExecutionPlan);
    //following will make sure that firstMock was called before secondMock
    inOrder.verify(mockedValidationService).validateESVersion(any(), any());
    inOrder.verify(mockedExecutionPlan).execute();
  }

  private UpgradeProcedure mockUpgradeProcedure(final UpgradeExecutionPlan mockedExecutionPlan) {
    return new UpgradeProcedure() {
      @Override
      public String getInitialVersion() {
        return "2.5.0";
      }

      @Override
      public String getTargetVersion() {
        return "2.6.0";
      }

      @Override
      protected UpgradePlan buildUpgradePlan() {
        return mockedExecutionPlan;
      }
    };
  }
}

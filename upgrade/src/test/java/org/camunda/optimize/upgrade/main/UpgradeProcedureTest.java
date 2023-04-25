/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.main;

import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.metadata.PreviousVersion;
import org.camunda.optimize.service.metadata.Version;
import org.camunda.optimize.upgrade.es.SchemaUpgradeClient;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.indices.UserTestIndex;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.service.UpgradeStepLogService;
import org.camunda.optimize.upgrade.service.UpgradeValidationService;
import org.camunda.optimize.upgrade.steps.schema.CreateIndexStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UpgradeProcedureTest {
  private static final String TARGET_VERSION = Version.VERSION;
  private static final String FROM_VERSION = PreviousVersion.PREVIOUS_VERSION;

  @Spy
  private final CreateIndexStep createIndexStep = new CreateIndexStep(new UserTestIndex(1));
  @Mock
  private SchemaUpgradeClient schemaUpgradeClient;
  @Mock
  private UpgradeValidationService validationService;
  @Mock
  private UpgradeStepLogService upgradeStepLogService;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private OptimizeElasticsearchClient esClient;

  @BeforeEach
  public void before() {
    // this method might be called by some tests
    // for the scope of these tests the value is not relevant though, thus returning static value
    lenient().when(esClient.getIndexNameService().getOptimizeIndexNameWithVersion(any()))
      .thenReturn("test");
  }

  @Test
  public void initializeSchemaIsNotCalled() {
    // given
    final UpgradeProcedure underTest = createUpgradeProcedure();
    when(schemaUpgradeClient.getSchemaVersion()).thenReturn(Optional.of(FROM_VERSION));

    // when
    underTest.performUpgrade(createUpgradePlan());

    // then schema isn't initialized, as this is handled instead by the main Upgrade class
    verify(schemaUpgradeClient, never()).initializeSchema();
  }

  @Test
  public void validationIsDoneBeforeUpgradeExecution() {
    // given
    final UpgradeProcedure underTest = createUpgradeProcedure();
    when(schemaUpgradeClient.getSchemaVersion()).thenReturn(Optional.of(FROM_VERSION));

    // when
    underTest.performUpgrade(createUpgradePlan());

    // then the validation and execution happens in the expected order
    InOrder inOrder = inOrder(validationService, createIndexStep);
    // The validation order matters since we first need to ensure that the ES client
    // is able to communicate to ElasticSearch before using it to retrieve the schema version.
    inOrder.verify(validationService).validateESVersion(any(), any());
    inOrder.verify(validationService).validateSchemaVersions(FROM_VERSION, FROM_VERSION, TARGET_VERSION);
    inOrder.verify(createIndexStep).execute(eq(schemaUpgradeClient));
  }

  @Test
  public void successfulUpgradeStepIsLogged() {
    // given
    final UpgradeProcedure underTest = createUpgradeProcedure();
    when(schemaUpgradeClient.getSchemaVersion()).thenReturn(Optional.of(FROM_VERSION));

    // when
    underTest.performUpgrade(createUpgradePlan());

    // then the validation and execution happens in the expected order
    InOrder inOrder = inOrder(createIndexStep, upgradeStepLogService);
    inOrder.verify(createIndexStep).execute(eq(schemaUpgradeClient));
    inOrder.verify(upgradeStepLogService).recordAppliedStep(any(), any());
  }

  @Test
  public void failedUpgradeStepIsNotLogged() {
    // given
    final UpgradeProcedure underTest = createUpgradeProcedure();
    when(schemaUpgradeClient.getSchemaVersion()).thenReturn(Optional.of(FROM_VERSION));
    doThrow(new UpgradeRuntimeException("failure")).when(createIndexStep).execute(any());

    // when
    final UpgradePlan upgradePlan = createUpgradePlan();
    assertThrows(UpgradeRuntimeException.class, () -> underTest.performUpgrade(upgradePlan));

    // then the validation and execution happens in the expected order
    InOrder inOrder = inOrder(createIndexStep, upgradeStepLogService);
    inOrder.verify(createIndexStep).execute(eq(schemaUpgradeClient));
    inOrder.verify(upgradeStepLogService, never()).recordAppliedStep(any(), any());
  }

  @Test
  public void executionSkippedOnSchemaVersionEqualToTargetVersion() {
    // given
    when(schemaUpgradeClient.getSchemaVersion()).thenReturn(Optional.of(TARGET_VERSION));
    final UpgradeProcedure underTest = createUpgradeProcedure();
    final UpgradePlan upgradePlan = Mockito.spy(createUpgradePlan());

    // when
    underTest.performUpgrade(upgradePlan);

    // then
    verify(validationService, never()).validateSchemaVersions(TARGET_VERSION, FROM_VERSION, TARGET_VERSION);
    verify(upgradePlan, never()).getUpgradeSteps();
  }

  private UpgradePlan createUpgradePlan() {
    return UpgradePlanBuilder.createUpgradePlan()
      .fromVersion(FROM_VERSION)
      .toVersion(TARGET_VERSION)
      .addUpgradeStep(createIndexStep)
      .build();
  }

  private UpgradeProcedure createUpgradeProcedure() {
    return new UpgradeProcedure(esClient, validationService, schemaUpgradeClient, upgradeStepLogService);
  }
}

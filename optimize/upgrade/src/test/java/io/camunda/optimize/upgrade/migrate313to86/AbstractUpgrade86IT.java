/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.migrate313to86;

import io.camunda.optimize.upgrade.AbstractUpgradeIT;
import io.camunda.optimize.upgrade.migrate313to86.indices.EventIndexV4;
import io.camunda.optimize.upgrade.migrate313to86.indices.EventProcessDefinitionIndexV5;
import io.camunda.optimize.upgrade.migrate313to86.indices.EventProcessMappingIndexV4;
import io.camunda.optimize.upgrade.migrate313to86.indices.EventPublishStateIndexV4;
import io.camunda.optimize.upgrade.migrate313to86.indices.EventSequenceCountIndexV4;
import io.camunda.optimize.upgrade.migrate313to86.indices.EventTraceStateIndexV2;
import io.camunda.optimize.upgrade.migrate313to86.indices.ImportIndexIndexV3;
import io.camunda.optimize.upgrade.migrate313to86.indices.LicenseIndexV3;
import io.camunda.optimize.upgrade.migrate313to86.indices.OnboardingStateIndexV2;
import io.camunda.optimize.upgrade.migrate313to86.indices.ProcessInstanceArchiveIndexV8;
import io.camunda.optimize.upgrade.migrate313to86.indices.SettingsIndexV2;
import io.camunda.optimize.upgrade.plan.UpgradePlan;
import io.camunda.optimize.upgrade.plan.UpgradePlanRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;

public class AbstractUpgrade86IT extends AbstractUpgradeIT {

  protected static final String FROM_VERSION = "3.13.0";
  protected static final String TO_VERSION = "8.6.0";
  private static final String EXAMPLE_PROCESS_KEY_1 = "aDefinitionKey1";
  private static final String EXAMPLE_PROCESS_KEY_2 = "aDefinitionKey2";

  protected final OnboardingStateIndexV2 ONBOARDING_STATE_INDEX = new OnboardingStateIndexV2();
  protected final SettingsIndexV2 SETTINGS_INDEX = new SettingsIndexV2();
  protected final EventIndexV4 EVENT_INDEX = new EventIndexV4();
  protected final EventProcessDefinitionIndexV5 EVENT_PROCESS_DEFINITION_INDEX =
      new EventProcessDefinitionIndexV5();
  protected final EventProcessMappingIndexV4 EVENT_PROCESS_MAPPING_INDEX =
      new EventProcessMappingIndexV4();
  protected final EventPublishStateIndexV4 EVENT_PUBLISH_STATE_INDEX =
      new EventPublishStateIndexV4();
  protected final EventSequenceCountIndexV4 EVENT_SEQUENCE_COUNT_EXTERNAL_INDEX =
      new EventSequenceCountIndexV4();
  protected final EventTraceStateIndexV2 EVENT_TRACE_STATE_EXTERNAL_INDEX =
      new EventTraceStateIndexV2();
  protected final ProcessInstanceArchiveIndexV8 PROCESS_INSTANCE_ARCHIVE_INDEX_1 =
      new ProcessInstanceArchiveIndexV8(EXAMPLE_PROCESS_KEY_1);
  protected final ProcessInstanceArchiveIndexV8 PROCESS_INSTANCE_ARCHIVE_INDEX_2 =
      new ProcessInstanceArchiveIndexV8(EXAMPLE_PROCESS_KEY_2);
  protected final LicenseIndexV3 LICENSE_INDEX = new LicenseIndexV3();
  protected final ImportIndexIndexV3 IMPORT_INDEX_INDEX = new ImportIndexIndexV3();

  @Override
  @BeforeEach
  protected void setUp() throws Exception {
    super.setUp();
    initSchema(
        List.of(
            ONBOARDING_STATE_INDEX,
            IMPORT_INDEX_INDEX,
            SETTINGS_INDEX,
            EVENT_INDEX,
            EVENT_PROCESS_DEFINITION_INDEX,
            EVENT_PROCESS_MAPPING_INDEX,
            EVENT_PUBLISH_STATE_INDEX,
            EVENT_SEQUENCE_COUNT_EXTERNAL_INDEX,
            EVENT_TRACE_STATE_EXTERNAL_INDEX,
            PROCESS_INSTANCE_ARCHIVE_INDEX_1,
            PROCESS_INSTANCE_ARCHIVE_INDEX_2,
            LICENSE_INDEX));
    setMetadataVersion(FROM_VERSION);
  }

  protected void performUpgrade() {
    final List<UpgradePlan> upgradePlans =
        new UpgradePlanRegistry(upgradeDependencies)
            .getSequentialUpgradePlansToTargetVersion(TO_VERSION);
    upgradePlans.forEach(plan -> upgradeProcedure.performUpgrade(plan));
  }
}

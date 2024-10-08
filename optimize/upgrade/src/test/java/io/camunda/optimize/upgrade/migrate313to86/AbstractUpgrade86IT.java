/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.migrate313to86;

import io.camunda.optimize.service.db.es.schema.index.index.PositionBasedImportIndexES;
import io.camunda.optimize.service.db.os.schema.index.index.PositionBasedImportIndexOS;
import io.camunda.optimize.service.db.schema.index.index.PositionBasedImportIndex;
import io.camunda.optimize.service.util.configuration.DatabaseType;
import io.camunda.optimize.upgrade.AbstractUpgradeIT;
import io.camunda.optimize.upgrade.migrate313to86.indices.db.EventIndexV4;
import io.camunda.optimize.upgrade.migrate313to86.indices.db.EventProcessDefinitionIndexV5;
import io.camunda.optimize.upgrade.migrate313to86.indices.db.EventProcessMappingIndexV4;
import io.camunda.optimize.upgrade.migrate313to86.indices.db.EventProcessPublishStateIndexV4;
import io.camunda.optimize.upgrade.migrate313to86.indices.db.EventSequenceCountIndexV4;
import io.camunda.optimize.upgrade.migrate313to86.indices.db.EventTraceStateIndexV2;
import io.camunda.optimize.upgrade.migrate313to86.indices.db.ImportIndexIndexV3;
import io.camunda.optimize.upgrade.migrate313to86.indices.db.LicenseIndexV3;
import io.camunda.optimize.upgrade.migrate313to86.indices.db.OnboardingStateIndexV2;
import io.camunda.optimize.upgrade.migrate313to86.indices.db.ProcessInstanceArchiveIndexV8;
import io.camunda.optimize.upgrade.migrate313to86.indices.db.SettingsIndexV2;
import io.camunda.optimize.upgrade.migrate313to86.indices.es.EventIndexV4ES;
import io.camunda.optimize.upgrade.migrate313to86.indices.es.EventProcessDefinitionIndexV5ES;
import io.camunda.optimize.upgrade.migrate313to86.indices.es.EventProcessMappingIndexV4ES;
import io.camunda.optimize.upgrade.migrate313to86.indices.es.EventProcessPublishStateIndexV4ES;
import io.camunda.optimize.upgrade.migrate313to86.indices.es.EventSequenceCountIndexV4ES;
import io.camunda.optimize.upgrade.migrate313to86.indices.es.EventTraceStateIndexV2ES;
import io.camunda.optimize.upgrade.migrate313to86.indices.es.ImportIndexIndexV3ES;
import io.camunda.optimize.upgrade.migrate313to86.indices.es.LicenseIndexV3ES;
import io.camunda.optimize.upgrade.migrate313to86.indices.es.OnboardingStateIndexV2ES;
import io.camunda.optimize.upgrade.migrate313to86.indices.es.ProcessInstanceArchiveIndexV8ES;
import io.camunda.optimize.upgrade.migrate313to86.indices.es.SettingsIndexV2ES;
import io.camunda.optimize.upgrade.migrate313to86.indices.os.EventIndexV4OS;
import io.camunda.optimize.upgrade.migrate313to86.indices.os.EventProcessDefinitionIndexV5OS;
import io.camunda.optimize.upgrade.migrate313to86.indices.os.EventProcessMappingIndexV4OS;
import io.camunda.optimize.upgrade.migrate313to86.indices.os.EventProcessPublishStateIndexV4OS;
import io.camunda.optimize.upgrade.migrate313to86.indices.os.EventSequenceCountIndexV4OS;
import io.camunda.optimize.upgrade.migrate313to86.indices.os.EventTraceStateIndexV2OS;
import io.camunda.optimize.upgrade.migrate313to86.indices.os.ImportIndexIndexV3OS;
import io.camunda.optimize.upgrade.migrate313to86.indices.os.LicenseIndexV3OS;
import io.camunda.optimize.upgrade.migrate313to86.indices.os.OnboardingStateIndexV2OS;
import io.camunda.optimize.upgrade.migrate313to86.indices.os.ProcessInstanceArchiveIndexV8OS;
import io.camunda.optimize.upgrade.migrate313to86.indices.os.SettingsIndexV2OS;
import io.camunda.optimize.upgrade.plan.UpgradePlan;
import io.camunda.optimize.upgrade.plan.UpgradePlanRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;

public class AbstractUpgrade86IT extends AbstractUpgradeIT {

  protected static final String FROM_VERSION = "3.13.0";
  protected static final String TO_VERSION = "8.6.0";
  private static final String EXAMPLE_PROCESS_KEY_1 = "aDefinitionKey1";
  private static final String EXAMPLE_PROCESS_KEY_2 = "aDefinitionKey2";

  protected OnboardingStateIndexV2 ONBOARDING_STATE_INDEX;
  protected SettingsIndexV2 SETTINGS_INDEX;
  protected PositionBasedImportIndex POSITION_BASED_IMPORT_INDEX;
  protected ProcessInstanceArchiveIndexV8 PROCESS_INSTANCE_ARCHIVE_INDEX_1;
  protected ProcessInstanceArchiveIndexV8 PROCESS_INSTANCE_ARCHIVE_INDEX_2;
  protected EventIndexV4 EVENT_INDEX;
  protected EventProcessDefinitionIndexV5 EVENT_PROCESS_DEFINITION_INDEX;
  protected EventProcessMappingIndexV4 EVENT_PROCESS_MAPPING_INDEX;
  protected EventProcessPublishStateIndexV4 EVENT_PUBLISH_STATE_INDEX;
  protected EventSequenceCountIndexV4 EVENT_SEQUENCE_COUNT_EXTERNAL_INDEX;
  protected EventTraceStateIndexV2 EVENT_TRACE_STATE_EXTERNAL_INDEX;
  protected LicenseIndexV3 LICENSE_INDEX;
  protected ImportIndexIndexV3 IMPORT_INDEX_INDEX;

  @Override
  @BeforeEach
  protected void setUp() throws Exception {
    super.setUp();
    instantiateProperIndices(databaseIntegrationTestExtension.getDatabaseVendor());
    initSchema(
        List.of(
            ONBOARDING_STATE_INDEX,
            SETTINGS_INDEX,
            PROCESS_INSTANCE_ARCHIVE_INDEX_1,
            PROCESS_INSTANCE_ARCHIVE_INDEX_2,
            POSITION_BASED_IMPORT_INDEX,
            EVENT_INDEX,
            EVENT_PROCESS_DEFINITION_INDEX,
            EVENT_PROCESS_MAPPING_INDEX,
            EVENT_PUBLISH_STATE_INDEX,
            EVENT_SEQUENCE_COUNT_EXTERNAL_INDEX,
            EVENT_TRACE_STATE_EXTERNAL_INDEX,
            LICENSE_INDEX,
            IMPORT_INDEX_INDEX));
    setMetadataVersion(FROM_VERSION);
  }

  protected void performUpgrade() {
    final List<UpgradePlan> upgradePlans =
        new UpgradePlanRegistry(upgradeDependencies)
            .getSequentialUpgradePlansToTargetVersion(TO_VERSION);
    upgradePlans.forEach(plan -> upgradeProcedure.performUpgrade(plan));
  }

  private void instantiateProperIndices(DatabaseType databaseVendor) {
    if (!isElasticSearchUpgrade()) {
      ONBOARDING_STATE_INDEX = new OnboardingStateIndexV2OS();
      SETTINGS_INDEX = new SettingsIndexV2OS();
      PROCESS_INSTANCE_ARCHIVE_INDEX_1 = new ProcessInstanceArchiveIndexV8OS(EXAMPLE_PROCESS_KEY_1);
      PROCESS_INSTANCE_ARCHIVE_INDEX_2 = new ProcessInstanceArchiveIndexV8OS(EXAMPLE_PROCESS_KEY_2);
      POSITION_BASED_IMPORT_INDEX = new PositionBasedImportIndexOS();
      EVENT_INDEX = new EventIndexV4OS();
      EVENT_PROCESS_DEFINITION_INDEX = new EventProcessDefinitionIndexV5OS();
      EVENT_PROCESS_MAPPING_INDEX = new EventProcessMappingIndexV4OS();
      EVENT_PUBLISH_STATE_INDEX = new EventProcessPublishStateIndexV4OS();
      EVENT_SEQUENCE_COUNT_EXTERNAL_INDEX = new EventSequenceCountIndexV4OS();
      EVENT_TRACE_STATE_EXTERNAL_INDEX = new EventTraceStateIndexV2OS();
      LICENSE_INDEX = new LicenseIndexV3OS();
      IMPORT_INDEX_INDEX = new ImportIndexIndexV3OS();
    } else {
      ONBOARDING_STATE_INDEX = new OnboardingStateIndexV2ES();
      SETTINGS_INDEX = new SettingsIndexV2ES();
      PROCESS_INSTANCE_ARCHIVE_INDEX_1 = new ProcessInstanceArchiveIndexV8ES(EXAMPLE_PROCESS_KEY_1);
      PROCESS_INSTANCE_ARCHIVE_INDEX_2 = new ProcessInstanceArchiveIndexV8ES(EXAMPLE_PROCESS_KEY_2);
      POSITION_BASED_IMPORT_INDEX = new PositionBasedImportIndexES();
      EVENT_INDEX = new EventIndexV4ES();
      EVENT_PROCESS_DEFINITION_INDEX = new EventProcessDefinitionIndexV5ES();
      EVENT_PROCESS_MAPPING_INDEX = new EventProcessMappingIndexV4ES();
      EVENT_PUBLISH_STATE_INDEX = new EventProcessPublishStateIndexV4ES();
      EVENT_SEQUENCE_COUNT_EXTERNAL_INDEX = new EventSequenceCountIndexV4ES();
      EVENT_TRACE_STATE_EXTERNAL_INDEX = new EventTraceStateIndexV2ES();
      LICENSE_INDEX = new LicenseIndexV3ES();
      IMPORT_INDEX_INDEX = new ImportIndexIndexV3ES();
    }
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate312to313;

import static org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder.createDefaultConfiguration;
import static org.camunda.optimize.upgrade.EnvironmentConfigUtil.createEmptyEnvConfig;

import java.util.List;
import org.camunda.optimize.service.db.es.schema.index.index.PositionBasedImportIndexES;
import org.camunda.optimize.service.util.configuration.elasticsearch.DatabaseConnectionNodeConfiguration;
import org.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import org.camunda.optimize.test.it.extension.MockServerUtil;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.migrate312to313.indicies.MetadataIndexV3;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanRegistry;
import org.junit.jupiter.api.BeforeEach;

public abstract class AbstractUpgrade313IT extends AbstractUpgradeIT {

  protected static final String FROM_VERSION = "3.12.0";
  protected static final String TO_VERSION = "3.13.0";

  protected final PositionBasedImportIndexES POSITION_BASED_IMPORT_INDEX =
      new PositionBasedImportIndexES();
  protected final MetadataIndexV3 METADATA_INDEX_V3 = new MetadataIndexV3();

  @BeforeEach
  protected void setUp() throws Exception {
    this.configurationService = createDefaultConfiguration();
    final DatabaseConnectionNodeConfiguration elasticConfig =
        this.configurationService.getElasticSearchConfiguration().getFirstConnectionNode();

    this.dbMockServer = createElasticMock(elasticConfig);
    elasticConfig.setHost(MockServerUtil.MOCKSERVER_HOST);
    elasticConfig.setHttpPort(IntegrationTestConfigurationUtil.getDatabaseMockServerPort());

    setUpUpgradeDependenciesWithConfiguration(configurationService);
    cleanAllDataFromElasticsearch();
    createEmptyEnvConfig();

    prefixAwareClient.setSnapshotInProgressRetryDelaySeconds(1);

    initSchema(List.of(POSITION_BASED_IMPORT_INDEX, METADATA_INDEX_V3));
    setMetadataV3Version();
  }

  protected void performUpgrade() {
    final List<UpgradePlan> upgradePlans =
        new UpgradePlanRegistry(upgradeDependencies)
            .getSequentialUpgradePlansToTargetVersion(TO_VERSION);
    upgradePlans.forEach(plan -> upgradeProcedure.performUpgrade(plan));
  }

  protected void setMetadataV3Version() {
    metadataService.upsertMetadataV3(prefixAwareClient, AbstractUpgrade313IT.FROM_VERSION);
  }
}

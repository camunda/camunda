/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.qa.migration.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;

import io.camunda.operate.qa.util.TestContext;
import io.camunda.operate.qa.util.migration.TestFixture;
import io.camunda.operate.schema.migration.SchemaMigration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * This bean performs the main test logic, before assertions can be applied.
 * * Finds in application context list of test fixtures
 * * Select those that are configured to be included in upgrade path (test.properties file)
 * * Apply test fixtures one by one
 * * Migrate data till "version.current"
 */
@Component
public class MigrationRunner {

  private static final Logger logger = LoggerFactory.getLogger(MigrationRunner.class);

  @Autowired
  private TestContext testContext;

  @Autowired
  private List<TestFixture> testFixtures;

  private Map<String, TestFixture> testFixtureMap;

  @Value("${upgrade.path}")
  private String[] upgradePath;

  @PostConstruct
  private void init() {
    initTestFixtureMap();
    selectAndRunTestFixtures();
    runMigration();
  }

  private void initTestFixtureMap() {
    testFixtureMap = new HashMap<>();
    for (TestFixture testFixture: testFixtures) {
      testFixtureMap.put(testFixture.getVersion(), testFixture);
    }
  }

  private void selectAndRunTestFixtures() {
    logger.info("Upgrade path under test: {}", upgradePath);
    for (String version: upgradePath) {
      final TestFixture testFixture = testFixtureMap.get(version);
      if (testFixture == null) {
        throw new RuntimeException("No test fixture found for version " + version);
      }
      logger.info("************ Applying test fixture for v. {} ************", version);
      testFixture.setup(testContext);
    }
  }

  private void runMigration() {
    logger.info("************ Migrating data to current version ************");
    try {
      String[] args = new String[4];
      args[0] = "--camunda.operate.elasticsearch.host=" + testContext.getExternalElsHost();
      args[1] = "--camunda.operate.elasticsearch.port=" + testContext.getExternalElsPort();
      args[2] = "--camunda.operate.zeebeelasticsearch.host=" + testContext.getExternalElsHost();
      args[3] = "--camunda.operate.zeebeelasticsearch.port=" + testContext.getExternalElsPort();
      SchemaMigration.main(args);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

}

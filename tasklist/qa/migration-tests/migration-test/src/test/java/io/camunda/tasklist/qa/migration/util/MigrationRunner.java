/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.qa.migration.util;

import io.camunda.tasklist.qa.util.TestContext;
import io.camunda.tasklist.qa.util.migration.TestFixture;
import io.camunda.tasklist.schema.migration.SchemaMigration;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * This bean performs the main test logic, before assertions can be applied. * Finds in application
 * context list of test fixtures * Select those that are configured to be included in upgrade path
 * (test.properties file) * Apply test fixtures one by one * Migrate data till "version.current"
 */
@Component
public class MigrationRunner {

  private static final Logger LOGGER = LoggerFactory.getLogger(MigrationRunner.class);

  @Autowired private TestContext testContext;

  @Autowired private List<TestFixture> testFixtures;

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
    for (TestFixture testFixture : testFixtures) {
      testFixtureMap.put(testFixture.getVersion(), testFixture);
    }
  }

  private void selectAndRunTestFixtures() {
    LOGGER.info("Upgrade path under test: {}", upgradePath);
    for (String version : upgradePath) {
      final TestFixture testFixture = testFixtureMap.get(version);
      if (testFixture == null) {
        throw new RuntimeException("No test fixture found for version " + version);
      }
      LOGGER.info("************ Applying test fixture for v. {} ************", version);
      testFixture.setup(testContext);
    }
  }

  private void runMigration() {
    LOGGER.info("************ Migrating data to current version ************");
    try {
      final String[] args = new String[2];
      args[0] =
          "--camunda.tasklist.elasticsearch.url="
              + String.format(
                  "http://%s:%s",
                  testContext.getExternalElsHost(), testContext.getExternalElsPort());
      args[1] =
          "--camunda.tasklist.zeebeelasticsearch.url="
              + String.format(
                  "http://%s:%s",
                  testContext.getExternalElsHost(), testContext.getExternalElsPort());
      SchemaMigration.main(args);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}

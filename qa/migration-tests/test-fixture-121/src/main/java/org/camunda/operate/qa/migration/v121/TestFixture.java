/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.qa.migration.v121;

import org.camunda.operate.qa.util.migration.AbstractTestFixture;
import org.camunda.operate.qa.util.migration.TestContext;
import org.camunda.operate.schema.migration.SchemaMigration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.testcontainers.utility.MountableFile;
import io.zeebe.containers.ZeebeBrokerContainer;

@Component
public class TestFixture extends AbstractTestFixture {

  private static final Logger logger = LoggerFactory.getLogger(TestFixture.class);

  public static final String OPERATE_VERSION = "1.2.1";
  private static final String ZEEBE_VERSION = "0.22.1";
  private static final String ZEEBE_CFG_YAML_FILE = "/zeebe-config/zeebe.cfg.toml";

  @Autowired
  private Process121DataGenerator process121DataGenerator;

  @Override
  public void setup(TestContext testContext) {
    super.setup(testContext);

    createDestinationSchema();

    logger.info("Starting Migration from {} to {}", "1.2.0", OPERATE_VERSION);    //TODO guess the version dinamically
    //migrate data up to v. 1.2.1
    runMigration();

    startZeebeAndOperate();  //schema is already there
    generateData();
    stopZeebeAndOperate();
  }

  private void createDestinationSchema() {
    logger.info("Starting Operate {} to create schema on startup", OPERATE_VERSION);
    //start Operate to create schema
    startOperate(OPERATE_VERSION);
    logger.info("Stopping Operate {}", OPERATE_VERSION);
    //stop Operate - we need to restart Operate after manipulating with data to update local caches
    stopOperate();
  }

  private void runMigration() {
    try {
      String[] args = new String[]{
        "--camunda.operate.elasticsearch.host=" + testContext.getExternalElsHost(),
        "--camunda.operate.elasticsearch.port=" + testContext.getExternalElsPort(),
        "--camunda.operate.zeebeElasticsearch.host=" + testContext.getExternalElsHost(),
        "--camunda.operate.zeebeElasticsearch.port=" + testContext.getExternalElsPort(),
        "--camunda.operate.elasticsearch.createSchema=false",
          //migration specific properties
        "--camunda.operate.migration.destinationVersion=" + OPERATE_VERSION,
        "--camunda.operate.migration.sourceVersion=" + "1.2.0"     //TODO detect dynamically
      };
      SchemaMigration.main(args);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

  }

  @Override
  public String getVersion() {
    return OPERATE_VERSION;
  }

  @Override
  protected void startZeebeAndOperate() {
    startZeebe(ZEEBE_VERSION);
    startOperate(OPERATE_VERSION);
  }

  /**
   * Older versions of Zeebe has different config.
   * @param broker
   */
  @Override
  protected void addConfig(ZeebeBrokerContainer broker) {
    broker.withCopyFileToContainer(MountableFile.forClasspathResource(ZEEBE_CFG_YAML_FILE), "/usr/local/zeebe/conf/zeebe.cfg.toml");
  }

  private void generateData() {
    try {
      process121DataGenerator.createData(testContext);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

}

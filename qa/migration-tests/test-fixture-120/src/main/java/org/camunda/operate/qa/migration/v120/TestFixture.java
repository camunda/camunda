/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.qa.migration.v120;

import org.camunda.operate.qa.util.migration.AbstractTestFixture;
import org.camunda.operate.qa.util.migration.TestContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.testcontainers.utility.MountableFile;
import io.zeebe.containers.ZeebeBrokerContainer;

@Component
public class TestFixture extends AbstractTestFixture {

  public static final String OPERATE_VERSION = "1.2.0";
  private static final String ZEEBE_VERSION = "0.22.0";
  private static final String ZEEBE_CFG_YAML_FILE = "/zeebe-config/zeebe.cfg.toml";

  @Autowired
  private BasicProcessDataGenerator basicProcessDataGenerator;

  @Override
  public void setup(TestContext testContext) {
    super.setup(testContext);
    startZeebeAndOperate();
    generateData();
    stopZeebeAndOperate();
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
      basicProcessDataGenerator.createData(testContext);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package com.camunda.optimize.test.upgrade

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

abstract class BaseUpgradeIT {
  protected String previousVersion = System.properties.getProperty("previousVersion");
  protected String currentVersion = System.properties.getProperty("currentVersion");
  protected String buildDirectory = System.properties.getProperty("buildDirectory");
  protected Integer oldElasticPort = Integer.valueOf(System.properties.getProperty("oldElasticPort"));
  protected Integer newElasticPort = Integer.valueOf(System.properties.getProperty("newElasticPort"));

  protected FileWriter oldOptimizeOutputWriter;
  protected FileWriter newOptimizeOutputWriter;
  protected FileWriter upgradeOutputWriter;

  @BeforeEach
  void logFilePrepare() {
    oldOptimizeOutputWriter = new FileWriter(getOldOptimizeOutputLogPath())
    newOptimizeOutputWriter = new FileWriter(getNewOptimizeOutputLogPath())
    upgradeOutputWriter = new FileWriter(getOptimizeUpdateLogPath())
  }

  @AfterEach
  void logFileFlush() {
    oldOptimizeOutputWriter.flush();
    oldOptimizeOutputWriter.close();
    newOptimizeOutputWriter.flush();
    newOptimizeOutputWriter.close();
    upgradeOutputWriter.flush();
    upgradeOutputWriter.close();
  }

  abstract getLogFileKey()

  protected String getOptimizeUpdateLogPath() {
    "${buildDirectory}/${getLogFileKey()}-optimize-upgrade.log"
  }

  protected String getNewOptimizeOutputLogPath() {
    "${buildDirectory}/${getLogFileKey()}-new-optimize-startup.log"
  }

  protected String getOldOptimizeOutputLogPath() {
    "${buildDirectory}/${getLogFileKey()}-old-optimize-startup.log"
  }
}

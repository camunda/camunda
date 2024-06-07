/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package com.camunda.optimize.test.upgrade

import groovy.junit5.plugin.JUnit5Runner
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class UpgradeDataIT extends BaseUpgradeIT {
  private static final Logger log = LoggerFactory.getLogger(UpgradeDataIT.class);

  @Override
  def getLogFileKey() {
    return "update-data";
  }

  @Test
  void upgradeWithDataAndPerformRegressionTest() {
    // generate engine data to get some variance into the process data
    CamBpmDataGenerator.generate()
    // clean new elastic and clean old elastic
    def oldElasticClient = new ElasticClient("old", oldElasticPort)
    oldElasticClient.cleanIndicesAndTemplates()
    def newElasticClient = new ElasticClient("new", newElasticPort)
    newElasticClient.cleanIndicesAndTemplates()
    def oldOptimize = new OptimizeWrapper(previousVersion, buildDirectory, oldElasticPort)
    // license only needed for old Optimize as the new one would get it from elasticsearch
    oldOptimize.copyLicense(UpgradeDataIT.class.getResource("/OptimizeLicense.txt").getPath())
    def newOptimize = new OptimizeWrapper(currentVersion, buildDirectory, newElasticPort)

    try {
      // start old optimize and import data
      oldOptimize.start(newOptimizeOutputWriter)
      oldOptimize.waitForImportToFinish()
      oldElasticClient.refreshAll()

      // Generate Optimize data to migrate on old Optimize (reports etc.)
      def oldOptimizeDataGenerator = new OptimizeGeneratorWrapper(previousVersion, buildDirectory + "/generators")
      oldOptimizeDataGenerator.generateOptimizeData()
      oldOptimize.stop()

      oldElasticClient.createSnapshotRepository()
      newElasticClient.createSnapshotRepository()
      oldElasticClient.createSnapshot()
      newElasticClient.restoreSnapshot()
      oldElasticClient.deleteSnapshot()

      // run new optimize upgrade
      newOptimize.startUpgrade(upgradeOutputWriter)
      newOptimize.waitForUpgradeToFinish()
      newOptimize.start(newOptimizeOutputWriter)
      newOptimize.waitForImportToFinish()

      log.info("Running com.camunda.optimize.PostMigrationTest...")
      new JUnit5Runner().run(PostMigrationTest.class, new GroovyClassLoader())
      log.info("Finished running com.camunda.optimize.PostMigrationTest.")

      // run current version generator to ensure it works
      def newOptimizeDataGenerator = new OptimizeGeneratorWrapper(currentVersion, buildDirectory + "/generators")
      newOptimizeDataGenerator.generateOptimizeData()

      newOptimize.stop()
    } finally {
      oldElasticClient.close()
      newElasticClient.close()
      oldOptimize.stop()
      newOptimize.stop()
    }

  }

}

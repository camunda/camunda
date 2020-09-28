/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */


import groovy.junit5.plugin.JUnit5Runner
import groovy.transform.NullCheck

class UpgradeDataTest {
  @NullCheck
  static upgradeWithDataAndPerformRegressionTestTest(String previousVersion,
                                                     String currentVersion,
                                                     String buildDir,
                                                     Integer oldElasticPort,
                                                     Integer newElasticPort) {
    // generate engine data to get some variance into the process data
    CamBpmDataGenerator.generate()
    // clean new elastic and clean old elastic
    def oldElasticClient = new ElasticClient("old", oldElasticPort)
    oldElasticClient.cleanIndicesAndTemplates()
    def newElasticClient = new ElasticClient("new", newElasticPort)
    newElasticClient.cleanIndicesAndTemplates()
    def oldOptimize = new OptimizeWrapper(previousVersion, buildDir, oldElasticPort)
    // license only needed for old Optimize as the new one would get it from elasticsearch
    oldOptimize.copyLicense(UpgradeDataTest.class.getResource("OptimizeLicense.txt").getPath())
    def newOptimize = new OptimizeWrapper(currentVersion, buildDir, newElasticPort)
    try {
      // start old optimize and import data
      oldOptimize.start()
      oldOptimize.waitForImportToFinish()
      oldElasticClient.refreshAll()

      // Generate Optimize data to migrate on old Optimize (reports etc.)
      def oldOptimizeDataGenerator = new OptimizeGeneratorWrapper(
        previousVersion, buildDir + "/generators", "generator"
      )
      oldOptimizeDataGenerator.generateOptimizeData()
      oldOptimize.stop()

      oldElasticClient.createSnapshotRepository()
      newElasticClient.createSnapshotRepository()
      oldElasticClient.createSnapshot()
      newElasticClient.restoreSnapshot()
      oldElasticClient.deleteSnapshot()

      // run new optimize upgrade
      newOptimize.runUpgrade()
      newOptimize.start()
      newOptimize.waitForImportToFinish()

      println "Running PostMigrationTest..."
      new JUnit5Runner().run(PostMigrationTest.class, new GroovyClassLoader())
      println "Finished running PostMigrationTest."

      // run current version generator to ensure it works
      def newOptimizeDataGenerator = new OptimizeGeneratorWrapper(currentVersion, buildDir + "/generators")
      newOptimizeDataGenerator.generateOptimizeData()

      newOptimize.stop()
    } catch (Exception e) {
      System.err.println e.getMessage()
      throw e
    } finally {
      oldElasticClient.close()
      newElasticClient.close()
      oldOptimize.stop()
      newOptimize.stop()
    }

  }

}

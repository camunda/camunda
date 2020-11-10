/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */


import groovy.transform.NullCheck
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex

import static org.assertj.core.api.Assertions.assertThat
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.*

class UpgradeEsSchemaTest {
  @NullCheck
  static upgradeAndVerifySchemaIntegrityTest(String previousVersion,
                                             String currentVersion,
                                             String buildDir,
                                             Integer oldElasticPort,
                                             Integer newElasticPort) {
    // clean new elastic and clean old elastic
    def oldElasticClient = new ElasticClient("old", oldElasticPort)
    oldElasticClient.cleanIndicesAndTemplates()
    def newElasticClient = new ElasticClient("new", newElasticPort)
    newElasticClient.cleanIndicesAndTemplates()
    def oldOptimize = new OptimizeWrapper(previousVersion, buildDir, oldElasticPort)
    def newOptimize = new OptimizeWrapper(currentVersion, buildDir, newElasticPort)
    try {
      // start new optimize to obtain expected schema and settings
      newOptimize.start().consumeProcessOutput()
      newOptimize.waitForImportToFinish()
      newElasticClient.refreshAll()
      def expectedSettings = newElasticClient.getSettings()
      def expectedMappings = newElasticClient.getMappings()
      def expectedAliases = newElasticClient.getAliases()
      def expectedTemplates = newElasticClient.getTemplates()
      newOptimize.stop()
      newElasticClient.cleanIndicesAndTemplates()

      // start old optimize to prepare for upgrade
      oldOptimize.start().consumeProcessOutput()
      oldOptimize.waitForImportToFinish()
      oldElasticClient.refreshAll()
      oldOptimize.stop()

      // perform snapshot and restore on new es
      oldElasticClient.createSnapshotRepository()
      newElasticClient.createSnapshotRepository()
      oldElasticClient.createSnapshot()
      newElasticClient.restoreSnapshot()
      oldElasticClient.deleteSnapshot()

      // start a new async snapshot operation to ensure the upgrade is resilient to concurrently running snapshots
      newElasticClient.createSnapshot(false)
      // run the upgrade
      def upgradeLogPath = buildDir + "/optimize-upgrade.log"
      def optimizeUpgradeOutputWriter = new FileWriter(upgradeLogPath)
      newOptimize.runUpgrade().consumeProcessOutputStream(optimizeUpgradeOutputWriter)
      // stop/delete async snapshot operation as upgrade completed already
      newElasticClient.deleteSnapshot()

      // start new optimize
      def newOptimizeLogPath = buildDir + "/optimize-startup.log"
      def newOptimizeStartupOutputWriter = new FileWriter(newOptimizeLogPath)
      newOptimize.start().consumeProcessOutputStream(newOptimizeStartupOutputWriter)

      newOptimize.waitForImportToFinish()
      newOptimize.stop()

      optimizeUpgradeOutputWriter.flush()
      newOptimizeStartupOutputWriter.flush()

      println "Asserting expected index metadata..."
      assertThat(newElasticClient.getSettings()).isEqualTo(expectedSettings)
      assertThat(newElasticClient.getMappings()).isEqualTo(expectedMappings)
      assertThat(newElasticClient.getAliases()).isEqualTo(expectedAliases)
      assertThat(newElasticClient.getTemplates()).containsExactlyInAnyOrderElementsOf(expectedTemplates)
      println "Finished asserting expected index metadata!"

      println "Asserting expected instance data doc counts..."
      assertThat(newElasticClient.getDocumentCount(PROCESS_DEFINITION_INDEX_NAME))
        .as("Process Definition Document Count is not as expected")
        .isEqualTo(oldElasticClient.getDocumentCount(PROCESS_DEFINITION_INDEX_NAME))
      assertThat(newElasticClient.getDocumentCount(PROCESS_INSTANCE_INDEX_NAME))
        .as("Process Instance Document Count is not as expected")
        .isEqualTo(oldElasticClient.getDocumentCount(PROCESS_INSTANCE_INDEX_NAME))
      assertThat(newElasticClient.getNestedDocumentCount(PROCESS_INSTANCE_INDEX_NAME, ProcessInstanceIndex.EVENTS))
        .as("Process Instance Activity Document Count is not as expected")
        .isEqualTo(oldElasticClient.getNestedDocumentCount(PROCESS_INSTANCE_INDEX_NAME, ProcessInstanceIndex.EVENTS))
      assertThat(newElasticClient.getNestedDocumentCount(PROCESS_INSTANCE_INDEX_NAME, ProcessInstanceIndex.VARIABLES))
        .as("Process Instance Activity Variable Count is not as expected")
        .isEqualTo(oldElasticClient.getNestedDocumentCount(PROCESS_INSTANCE_INDEX_NAME, ProcessInstanceIndex.VARIABLES))
      assertThat(newElasticClient.getNestedDocumentCount(PROCESS_INSTANCE_INDEX_NAME, ProcessInstanceIndex.USER_TASKS))
        .as("Process Instance User Task Count is not as expected")
        .isEqualTo(oldElasticClient.getNestedDocumentCount(PROCESS_INSTANCE_INDEX_NAME, ProcessInstanceIndex.USER_TASKS))

      assertThat(oldElasticClient.getDocumentCount(DECISION_DEFINITION_INDEX_NAME))
        .as("Decision Definition Document Count is not as expected")
        .isEqualTo(newElasticClient.getDocumentCount(DECISION_DEFINITION_INDEX_NAME))
      assertThat(oldElasticClient.getDocumentCount(DECISION_INSTANCE_INDEX_NAME))
        .as("Decision Instance Document Count is not as expected")
        .isEqualTo(newElasticClient.getDocumentCount(DECISION_INSTANCE_INDEX_NAME))
      println "Finished asserting expected instance data doc counts..."

      println "Asserting on startup and upgrade errors..."
      new File(upgradeLogPath).eachLine { line ->
        // warns about snapshots in progress are fine
        def matcherWarn = line =~ /WARN(?!.*snapshot_in_progress_exception.*)/
        def matcherError = line =~ /ERROR/
        assertThat(matcherWarn.find()).withFailMessage("Upgrade log contained warn log: %s", line)
          .isFalse()
        assertThat(matcherError.find()).withFailMessage("Upgrade log contained error log: %s", line)
          .isFalse()
      }
      new File(newOptimizeLogPath).eachLine { line ->
        def matcherWarn = line =~ /WARN/
        def matcherError = line =~ /ERROR/
        assertThat(matcherWarn.find()).withFailMessage("Startup log contained warn log: %s", line)
          .isFalse()
        assertThat(matcherError.find()).withFailMessage("Startup log contained error log: %s", line)
          .isFalse()
      }
      println "Finished asserting on startup and upgrade errors"
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

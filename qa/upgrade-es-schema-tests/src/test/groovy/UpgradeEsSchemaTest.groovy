/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex

import static org.assertj.core.api.Assertions.assertThat
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.*

class UpgradeEsSchemaTest {
  static upgradeAndVerifySchemaIntegrityTest(String previousVersion,
                                             String currentVersion,
                                             String buildDir,
                                             Integer oldElasticPort,
                                             Integer newElasticPort) {
    // clean new elastic and clean old elastic
    def oldElasticClient = new ElasticClient("old", oldElasticPort)
    oldElasticClient.cleanIndices()
    def newElasticClient = new ElasticClient("new", newElasticPort)
    newElasticClient.cleanIndices()
    def oldOptimize = new OptimizeWrapper(previousVersion, buildDir, oldElasticPort)
    def newOptimize = new OptimizeWrapper(currentVersion, buildDir, newElasticPort)
    try {
      // start new optimize to obtain expected schema and settings
      newOptimize.start().consumeProcessOutput()
      newElasticClient.refreshAll()
      def expectedSettings = newElasticClient.getSettings()
      def expectedMappings = newElasticClient.getMappings()
      def expectedAliases = newElasticClient.getAliases()
      def expectedTemplates = newElasticClient.getTemplates()
      newOptimize.stop()
      newElasticClient.cleanIndices()

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

      // run the upgrade
      def optimizeUpgradeOutputWriter = new FileWriter("optimize-upgrade.log")
      newOptimize.runUpgrade().consumeProcessOutputStream(optimizeUpgradeOutputWriter)

      // start new optimize
      def newOptimizeStartupOutputWriter = new FileWriter("optimize-startup.log")
      newOptimize.start().consumeProcessOutputStream(newOptimizeStartupOutputWriter)

      newOptimize.waitForImportToFinish()
      newOptimize.stop()

      optimizeUpgradeOutputWriter.flush()
      newOptimizeStartupOutputWriter.flush()

      println "Asserting expected index metadata..."
      assertThat(newElasticClient.getSettings()).isEqualTo(expectedSettings)
      assertThat(newElasticClient.getMappings()).isEqualTo(expectedMappings)
      assertThat(newElasticClient.getAliases()).isEqualTo(expectedAliases)
      assertThat(newElasticClient.getTemplates()).isEqualTo(expectedTemplates)
      println "Finished asserting expected index metadata!"

      println "Asserting expected instance data doc counts..."
      assertThat(oldElasticClient.getDocumentCount(PROCESS_DEFINITION_INDEX_NAME))
        .isEqualTo(newElasticClient.getDocumentCount(PROCESS_DEFINITION_INDEX_NAME))
      assertThat(oldElasticClient.getDocumentCount(PROCESS_INSTANCE_INDEX_NAME))
        .isEqualTo(newElasticClient.getDocumentCount(PROCESS_INSTANCE_INDEX_NAME))
      assertThat(oldElasticClient.getNestedDocumentCount(PROCESS_INSTANCE_INDEX_NAME, ProcessInstanceIndex.EVENTS))
        .isEqualTo(newElasticClient.getNestedDocumentCount(PROCESS_INSTANCE_INDEX_NAME, ProcessInstanceIndex.EVENTS))
      assertThat(oldElasticClient.getNestedDocumentCount(PROCESS_INSTANCE_INDEX_NAME, ProcessInstanceIndex.VARIABLES))
        .isEqualTo(newElasticClient.getNestedDocumentCount(PROCESS_INSTANCE_INDEX_NAME, ProcessInstanceIndex.VARIABLES))
      assertThat(oldElasticClient.getNestedDocumentCount(PROCESS_INSTANCE_INDEX_NAME, ProcessInstanceIndex.USER_TASKS))
        .isEqualTo(newElasticClient.getNestedDocumentCount(PROCESS_INSTANCE_INDEX_NAME, ProcessInstanceIndex.USER_TASKS))

      assertThat(oldElasticClient.getDocumentCount(DECISION_DEFINITION_INDEX_NAME))
        .isEqualTo(newElasticClient.getDocumentCount(DECISION_DEFINITION_INDEX_NAME))
      assertThat(oldElasticClient.getDocumentCount(DECISION_INSTANCE_INDEX_NAME))
        .isEqualTo(newElasticClient.getDocumentCount(DECISION_INSTANCE_INDEX_NAME))
      println "Finished asserting expected instance data doc counts..."

      println "Asserting on startup and upgrade errors..."
      new File("optimize-upgrade.log").eachLine {line ->
        def matcherWarn = line =~ /WARN/
        def matcherError = line =~ /ERROR/
        assertThat(matcherWarn.find()).withFailMessage("Upgrade log contained warn log: %s", line).isEqualTo(false)
        assertThat(matcherError.find()).withFailMessage("Upgrade log contained error log: %s", line).isEqualTo(false)
      }
      new File("optimize-startup.log").eachLine {line ->
        def matcherWarn = line =~ /WARN/
        def matcherError = line =~ /ERROR/
        assertThat(matcherWarn.find()).withFailMessage("Startup log contained warn log: %s", line).isEqualTo(false)
        assertThat(matcherError.find()).withFailMessage("Startup log contained error log: %s", line).isEqualTo(false)
      }
      println "Finished asserting on startup and upgrade errors"
    } catch (Exception e) {
      System.err.println e.getMessage()
      System.exit(1)
    } finally {
      "rm optimize-upgrade.log".execute()
      "rm optimize-startup.log".execute()
      oldElasticClient.close()
      newElasticClient.close()
      oldOptimize.stop()
      newOptimize.stop()
    }
    System.exit(0)
  }

}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package com.camunda.optimize.test.upgrade

import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static org.assertj.core.api.Assertions.assertThat
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.*

class UpgradeEsSchemaIT extends BaseUpgradeIT {
  private static final Logger log = LoggerFactory.getLogger(UpgradeEsSchemaIT.class);

  @Override
  def getLogFileKey() {
    return "update-schema";
  }

  @Test
  void upgradeAndVerifySchemaIntegrityTest() {
    // clean new elastic and clean old elastic
    def oldElasticClient = new ElasticClient("old", oldElasticPort)
    oldElasticClient.cleanIndicesAndTemplates()
    def newElasticClient = new ElasticClient("new", newElasticPort)
    newElasticClient.cleanIndicesAndTemplates()
    def oldOptimize = new OptimizeWrapper(previousVersion, buildDirectory, oldElasticPort)
    def newOptimize = new OptimizeWrapper(currentVersion, buildDirectory, newElasticPort)

    try {
      // start new optimize to obtain expected schema and settings
      newOptimize.start(newOptimizeOutputWriter)
      newOptimize.waitForImportToFinish()
      newElasticClient.refreshAll()
      def expectedSettings = newElasticClient.getSettings()
      def expectedMappings = newElasticClient.getMappings()
      def expectedAliases = newElasticClient.getAliases()
      def expectedTemplates = newElasticClient.getTemplates()
      newOptimize.stop()
      newElasticClient.cleanIndicesAndTemplates()

      // start old optimize to prepare for upgrade
      oldOptimize.start(oldOptimizeOutputWriter)
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
      newOptimize.startUpgrade(upgradeOutputWriter)
      newOptimize.waitForUpgradeToFinish(360)

      // stop/delete async snapshot operation as upgrade completed already
      newElasticClient.deleteSnapshot()

      // start new optimize
      newOptimize.start(newOptimizeOutputWriter)
      newOptimize.waitForImportToFinish()
      newOptimize.stop()

      log.info("Asserting expected index metadata...")
      assertThat(newElasticClient.getSettings()).isEqualTo(expectedSettings)
      assertThat(newElasticClient.getMappings()).isEqualTo(expectedMappings)
      assertThat(newElasticClient.getAliases()).isEqualTo(expectedAliases)
      assertThat(newElasticClient.getTemplates()).containsExactlyInAnyOrderElementsOf(expectedTemplates)
      log.info("Finished asserting expected index metadata!")

      log.info("Asserting expected instance data doc counts...")
      assertThat(newElasticClient.getDocumentCount(PROCESS_DEFINITION_INDEX_NAME))
        .as("Process Definition Document Count is not as expected")
        .isEqualTo(oldElasticClient.getDocumentCount(PROCESS_DEFINITION_INDEX_NAME))
      assertThat(newElasticClient.getDocumentCount(PROCESS_INSTANCE_MULTI_ALIAS))
        .as("Process Instance Document Count is not as expected")
        .isEqualTo(oldElasticClient.getDocumentCount(PROCESS_INSTANCE_MULTI_ALIAS))
      assertThat(newElasticClient.getNestedDocumentCount(PROCESS_INSTANCE_MULTI_ALIAS, ProcessInstanceIndex.FLOW_NODE_INSTANCES))
        .as("Process Instance FlowNodeInstance Count is not as expected")
        .isEqualTo(oldElasticClient.getNestedDocumentCount(PROCESS_INSTANCE_MULTI_ALIAS, ProcessInstanceIndex.FLOW_NODE_INSTANCES))
      assertThat(newElasticClient.getNestedDocumentCount(PROCESS_INSTANCE_MULTI_ALIAS, ProcessInstanceIndex.VARIABLES))
        .as("Process Instance Variable Count is not as expected")
        .isEqualTo(oldElasticClient.getNestedDocumentCount(PROCESS_INSTANCE_MULTI_ALIAS, ProcessInstanceIndex.VARIABLES))

      assertThat(oldElasticClient.getDocumentCount(DECISION_DEFINITION_INDEX_NAME))
        .as("Decision Definition Document Count is not as expected")
        .isEqualTo(newElasticClient.getDocumentCount(DECISION_DEFINITION_INDEX_NAME))
      assertThat(oldElasticClient.getDocumentCount(DECISION_INSTANCE_MULTI_ALIAS))
        .as("Decision Instance Document Count is not as expected")
        .isEqualTo(newElasticClient.getDocumentCount(DECISION_INSTANCE_MULTI_ALIAS))
      log.info("Finished asserting expected instance data doc counts...")

      log.info("Asserting on startup and upgrade errors...")
      new File(getOptimizeUpdateLogPath()).eachLine { line ->
        // warns about snapshots in progress are fine
        def matcherWarn = line =~ /WARN(?!.*snapshot_in_progress_exception.*)/
        def matcherError = line =~ /ERROR/
        assertThat(matcherWarn.find()).withFailMessage("Upgrade log contained warn log: %s", line)
          .isFalse()
        assertThat(matcherError.find()).withFailMessage("Upgrade log contained error log: %s", line)
          .isFalse()
      }
      new File(getNewOptimizeOutputLogPath()).eachLine { line ->
        // ignore warns about incorrect serializationDataFormat in default processes from ObjectVariableFlatteningService
        def matcherWarn = line =~ /WARN(?!.*ObjectVariableService - Object variable 'approverGroups'*)/
        def matcherError = line =~ /ERROR/
        assertThat(matcherWarn.find()).withFailMessage("Startup log contained warn log: %s", line)
          .isFalse()
        assertThat(matcherError.find()).withFailMessage("Startup log contained error log: %s", line)
          .isFalse()
      }
      log.info("Finished asserting on startup and upgrade errors")
    } finally {
      oldElasticClient.close()
      newElasticClient.close()
      oldOptimize.stop()
      newOptimize.stop()
    }
  }

}

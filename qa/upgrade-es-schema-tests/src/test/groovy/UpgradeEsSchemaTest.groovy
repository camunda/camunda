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
      newOptimize.start()
      newElasticClient.refreshAll()
      def expectedSettings = newElasticClient.getSettings()
      def expectedMappings = newElasticClient.getMappings()
      def expectedAliases = newElasticClient.getAliases()
      def expectedTemplates = newElasticClient.getTemplates()
      newOptimize.stop()
      newElasticClient.cleanIndices()

      // start old optimize to prepare for upgrade
      oldOptimize.start()
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
      newOptimize.runUpgrade()
      newOptimize.start()
      newOptimize.waitForImportToFinish()
      newOptimize.stop()

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
    } catch (Exception e) {
      System.err.println e.getMessage()
      System.exit(1)
    } finally {
      oldElasticClient.close()
      newElasticClient.close()
      oldOptimize.stop()
      newOptimize.stop()
    }
    System.exit(0)
  }

}

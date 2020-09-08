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
    // #1 clean new elastic and clean old elastic
    def oldElasticClient = new ElasticClient("old", oldElasticPort)
    oldElasticClient.cleanIndices()
    def newElasticClient = new ElasticClient("new", newElasticPort)
    newElasticClient.cleanIndices()
    def oldOptimize = new OptimizeWrapper(previousVersion, buildDir, oldElasticPort)
    def newOptimize = new OptimizeWrapper(currentVersion, buildDir, newElasticPort)
    // #3 start new optimize (waiting for it to be running)
    newOptimize.start()
    newElasticClient.refreshAll()
    // #4 get settings etc. and save to variables
    def expectedSettings = newElasticClient.getSettings()
    def expectedMappings = newElasticClient.getMappings()
    def expectedAliases = newElasticClient.getAliases()
    def expectedTemplates = newElasticClient.getTemplates()
    // #5 kill new optimize & clean elastic
    newOptimize.stop()
    newElasticClient.cleanIndices()
    // #6 start old optimize
    oldOptimize.start()
    // #7 wait for import to finish
    oldOptimize.waitForImportToFinish(60)
    oldElasticClient.refreshAll()
    // #8 stop old optimize
    oldOptimize.stop()
    // #9 perform snapshot and restore on new es
    oldElasticClient.createSnapshotRepository()
    newElasticClient.createSnapshotRepository()
    oldElasticClient.createSnapshot()
    newElasticClient.restoreSnapshot()
    oldElasticClient.deleteSnapshot()
    // #10 run new optimize upgrade
    newOptimize.runUpgrade()
    // #11 start new optimize
    newOptimize.start()
    // #12 wait for import to finish
    newOptimize.waitForImportToFinish(60)
    newOptimize.stop()
    // #13 do assertion of settings
    println "Asserting expected index metadata..."
    assertThat(newElasticClient.getSettings()).isEqualTo(expectedSettings)
    assertThat(newElasticClient.getMappings()).isEqualTo(expectedMappings)
    assertThat(newElasticClient.getAliases()).isEqualTo(expectedAliases)
    assertThat(newElasticClient.getTemplates()).isEqualTo(expectedTemplates)
    println "Finished asserting expected index metadata!"
    // #14 do assertions on doc counts
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
  }

}

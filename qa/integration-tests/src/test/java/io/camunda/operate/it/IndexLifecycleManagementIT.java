/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.it;

import io.camunda.operate.archiver.Archiver;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.util.SearchTestRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.function.Consumer;

public class IndexLifecycleManagementIT extends OperateZeebeAbstractIT {
  @Rule
  public SearchTestRule searchTestRule = new SearchTestRule(operatePropertiesCustomizer());

  public static Consumer<OperateProperties> operatePropertiesCustomizer() {
    return operateProperties -> {
      operateProperties.getArchiver().setIlmEnabled(true);
      operateProperties.getArchiver().setIlmMinAgeForDeleteArchivedIndices("1m");
    };
  }

  @Autowired
  private Archiver archiver;

  @Autowired
  private ListViewTemplate processInstanceTemplate;

  @Before
  public void before() {
    super.before();
    clearMetrics();
  }

  @Test
  @Ignore("Marking with ignore, as it runs for around 15 minutes and for some reason fails on CI")
  public void testArchivedIndexIsDeletedByILM() throws Exception {
    // given (set up) : disabled OperationExecutor
    tester.disableOperationExecutor();
    // and given processInstance
    final String bpmnProcessId = "startEndProcess";
    final String taskId = "task";
    final BpmnModelInstance startEndProcess =
      Bpmn.createExecutableProcess(bpmnProcessId)
        .startEvent()
        .serviceTask(taskId).zeebeJobType(taskId)
        .endEvent()
        .done();

    var archivedIndex = archiver.getDestinationIndexName(processInstanceTemplate.getFullQualifiedName(), "_test_archived");

    tester
      .deployProcess(startEndProcess, "startEndProcess.bpmn")
      .processIsDeployed()
      .and()
      .startProcessInstance(bpmnProcessId)
      .waitUntil()
      .processInstanceIsStarted()
      .and()
      .completeTask(taskId)
      .waitUntil()
      .processInstanceIsFinished()
      .archive().waitUntil().archiveIsDone()
      .then()
      // Opensearch runs ISM-related operations on a schedule, which has something around 5 minutes run intervals.
      // Moving from initial state to final requires at least 2 scheduled runs.
      // Besides, index deletion itself seems to happen sort of asynchronous from the ISM scheduler runs.
      // In tests overall time taken to delete index by ISM was varying from around 700 to 1000 seconds,
      // so putting here a twice bigger timeout to cover all the corner cases.
      // Elastic appears to take even more time (~1200 seconds) to accomplish deletion
      .waitIndexDeletion(archivedIndex, 3600*1000);
  }
}
